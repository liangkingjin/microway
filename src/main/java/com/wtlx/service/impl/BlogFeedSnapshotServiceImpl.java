package com.wtlx.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wtlx.domain.entity.Blog;
import com.wtlx.repository.mapper.BlogMapper;
import com.wtlx.service.IBlogFeedSnapshotService;
import com.wtlx.utils.FeedConstants;
import com.wtlx.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 游记双列流的分批召回与缓存。
 * <p>
 * 一个批次 = 按时间倒序的 100 条游记，批内粗排后写入 ZSet。
 * 批次用「查询起点（时间戳, ID）」作为 key 的一部分，因此：
 * 1. 同一游标永远映射到同一批数据，缓存过期重建也得到相同结果，翻页不会重复；
 * 2. 不需要额外的指针、版本号或分布式锁，重复构建的结果一致，无害。
 */
@Slf4j
@Service
public class BlogFeedSnapshotServiceImpl implements IBlogFeedSnapshotService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BlogMapper blogMapper;

    @Override
    public FeedPage page(Long userId, String cursor, int pageSize) {
        int size = pageSize <= 0 ? 10 : Math.min(pageSize, FeedConstants.BLOG_MAX_PAGE_SIZE);
        Cursor batch = Cursor.parse(cursor);
        if (batch == null || batch.offset() >= FeedConstants.BLOG_BATCH_SIZE) {
            batch = Cursor.first();
        }

        try {
            return readBatch(userId, batch, size);
        } catch (Exception e) {
            log.warn("游记批次缓存不可用，降级数据库查询", e);
            return readFromDb(batch, size);
        }
    }

    /** 读一批中的一页；批内读完则用本批最早的一条开启下一批。 */
    private FeedPage readBatch(Long userId, Cursor c, int size) {
        String key = RedisConstants.FEED_BLOG_BATCH_KEY + (userId == null ? 0 : userId)
                + RedisConstants.FEED_BLOG_MEMBER_SEPARATOR + c.ts() + "_" + c.id();
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            buildBatch(key, c);
        }

        Set<String> slice = stringRedisTemplate.opsForZSet()
                .reverseRange(key, c.offset(), (long) c.offset() + size - 1);
        List<String> members = slice == null ? List.of() : new ArrayList<>(slice);
        List<Long> ids = members.stream().map(this::parseId).toList();

        Long card = stringRedisTemplate.opsForZSet().zCard(key);
        long total = card == null ? 0L : card;
        int nextOffset = c.offset() + members.size();

        Cursor next;
        if (nextOffset < total) {
            next = new Cursor(c.ts(), c.id(), nextOffset);
        } else if (total > 0) {
            // 不用 total == BATCH_SIZE：批次内游记被删后，不能让整条流提前结束
            next = oldestInBatch(key);
        } else {
            next = null;
        }
        return new FeedPage(ids, next == null ? null : next.encode(), next != null);
    }

    private Cursor oldestInBatch(String key) {
        Set<String> members = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        if (members == null || members.isEmpty()) {
            return null;
        }
        long minTs = Long.MAX_VALUE;
        long minId = Long.MAX_VALUE;
        for (String member : members) {
            int sep = member.indexOf(RedisConstants.FEED_BLOG_MEMBER_SEPARATOR);
            if (sep <= 0) {
                continue;
            }
            long id = Long.parseLong(member.substring(0, sep));
            long ts = Long.parseLong(member.substring(sep + 1));
            if (ts < minTs || (ts == minTs && id < minId)) {
                minTs = ts;
                minId = id;
            }
        }
        return minTs == Long.MAX_VALUE ? null : new Cursor(minTs, minId, 0);
    }

    /** 召回一批，按粗排位次一次写入 ZSet，读写两侧都只看到完整的一批。 */
    private void buildBatch(String key, Cursor c) {
        List<Blog> rows = queryBatch(c, FeedConstants.BLOG_BATCH_SIZE);
        if (rows.isEmpty()) {
            return;
        }

        double maxInteraction = rows.stream().mapToDouble(this::interaction).max().orElse(0D);
        record Ranked(Blog blog, double score) {
        }
        List<Ranked> ranked = rows.stream()
                .map(b -> new Ranked(b, rankScore(b, maxInteraction)))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(Comparator.comparingLong((Ranked r) -> r.blog().getId()).reversed()))
                .toList();

        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        for (int i = 0; i < ranked.size(); i++) {
            // score 用位次而非粗排分，避免同分导致批内顺序不稳定
            tuples.add(ZSetOperations.TypedTuple.of(toMember(ranked.get(i).blog()),
                    (double) (FeedConstants.BLOG_BATCH_SIZE - i)));
        }
        stringRedisTemplate.opsForZSet().add(key, tuples);
        stringRedisTemplate.expire(key, RedisConstants.FEED_BLOG_BATCH_TTL, TimeUnit.SECONDS);
    }

    /** 按时间游标取一批：时间倒序、ID 倒序，相邻批次不重叠。 */
    private List<Blog> queryBatch(Cursor c, int limit) {
        LocalDateTime pivot = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.ts()), ZoneId.systemDefault());
        return blogMapper.selectList(new QueryWrapper<Blog>()
                .select("id", "liked", "comments", "images", "create_time")
                .and(w -> w.lt("create_time", pivot)
                        .or(o -> o.eq("create_time", pivot).lt("id", c.id())))
                .orderByDesc("create_time", "id")
                .last("limit " + limit));
    }

    /** Redis 不可用：只按时间序兜底，不粗排。 */
    private FeedPage readFromDb(Cursor c, int size) {
        List<Blog> rows = queryBatch(c, size + 1);
        boolean hasMore = rows.size() > size;
        List<Blog> page = hasMore ? rows.subList(0, size) : rows;
        List<Long> ids = page.stream().map(Blog::getId).toList();

        String nextCursor = null;
        if (hasMore) {
            Blog last = page.get(page.size() - 1);
            nextCursor = new Cursor(toMillis(last.getCreateTime()), last.getId(), 0).encode();
        }
        return new FeedPage(ids, nextCursor, hasMore);
    }

    private double rankScore(Blog blog, double maxInteraction) {
        double interaction = interaction(blog);
        double normalized = maxInteraction <= 0 ? 0D : interaction / maxInteraction;
        double ageHours = blog.getCreateTime() == null ? 0D
                : Math.max(0D, Duration.between(blog.getCreateTime(), LocalDateTime.now()).toMinutes() / 60D);
        double freshness = Math.exp(-ageHours / FeedConstants.BLOG_FRESHNESS_HALF_LIFE_HOURS);
        int images = StrUtil.isBlank(blog.getImages()) ? 0 : blog.getImages().split(",").length;
        double imageRatio = Math.min(images, 9) / 9D;

        return FeedConstants.BLOG_WEIGHT_INTERACTION * normalized
                + FeedConstants.BLOG_WEIGHT_FRESHNESS * freshness
                + FeedConstants.BLOG_WEIGHT_IMAGE * imageRatio;
    }

    private double interaction(Blog blog) {
        int liked = blog.getLiked() == null ? 0 : blog.getLiked();
        int comments = blog.getComments() == null ? 0 : blog.getComments();
        return Math.log1p(liked * 3D + comments * 2D);
    }

    private String toMember(Blog blog) {
        return blog.getId() + RedisConstants.FEED_BLOG_MEMBER_SEPARATOR + toMillis(blog.getCreateTime());
    }

    private Long parseId(String member) {
        int sep = member.indexOf(RedisConstants.FEED_BLOG_MEMBER_SEPARATOR);
        return Long.valueOf(sep > 0 ? member.substring(0, sep) : member);
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 游标：{批次查询起点时间戳}.{批次查询起点ID}.{批内已读位置} */
    record Cursor(long ts, long id, int offset) {

        static Cursor first() {
            return new Cursor(System.currentTimeMillis(), Long.MAX_VALUE, 0);
        }

        static Cursor parse(String raw) {
            if (StrUtil.isBlank(raw)) {
                return null;
            }
            String[] parts = raw.trim().split("\\.");
            if (parts.length != 3) {
                return null;
            }
            try {
                return new Cursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                        Math.max(0, Integer.parseInt(parts[2])));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String encode() {
            return ts + "." + id + "." + offset;
        }
    }
}
