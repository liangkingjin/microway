package com.wtlx.service.impl;

import com.wtlx.entity.ThumbsUpRecord;
import com.wtlx.mapper.BlogMapper;
import com.wtlx.mq.ThumbsUpMessage;
import com.wtlx.service.IThumbsUpRecordService;
import com.wtlx.service.ILikedService;
import com.wtlx.service.ITbBlogCommentsService;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.RabbitMQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 点赞服务实现类
 * 使用Redis数据结构存储点赞数据：
 * - Set：存储点赞用户ID，用于快速判断用户是否点赞
 * - ZSet：存储点赞数量，score为点赞数
 *
 * 点赞时通过RabbitMQ异步更新业务表点赞数
 * 定时任务持久化点赞记录到thumbs_up_record表
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class LikedServiceImpl implements ILikedService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private ITbBlogCommentsService commentsService;

    @Resource
    private IThumbsUpRecordService thumbsUpRecordService;

    /**
     * 获取用户Set的Redis key
     *
     * @param type 内容类型
     * @param id   内容ID
     * @return Redis key
     */
    private String getUserKey(String type, Long id) {
        switch (type) {
            case ILikedService.TYPE_BLOG:
                return RedisConstants.BLOG_LIKED_USER_KEY + id;
            case ILikedService.TYPE_COMMENT:
                return RedisConstants.COMMENT_LIKED_USER_KEY + id;
            case ILikedService.TYPE_TRAVEL:
                return RedisConstants.TRAVEL_LIKED_USER_KEY + id;
            default:
                throw new IllegalArgumentException("未知的点赞类型: " + type);
        }
    }

    /**
     * 获取数量聚合ZSet的Redis key
     * 所有同类型实体的点赞数存在同一个ZSet里，member为实体ID
     *
     * @param type 内容类型
     * @return Redis key（固定值，不含ID）
     */
    private String getCountKey(String type) {
        switch (type) {
            case ILikedService.TYPE_BLOG:
                return RedisConstants.BLOG_LIKED_COUNT_KEY;
            case ILikedService.TYPE_COMMENT:
                return RedisConstants.COMMENT_LIKED_COUNT_KEY;
            case ILikedService.TYPE_TRAVEL:
                return RedisConstants.TRAVEL_LIKED_COUNT_KEY;
            default:
                throw new IllegalArgumentException("未知的点赞类型: " + type);
        }
    }

    /**
     * 点赞/取消点赞
     * - Set添加/移除用户ID（判断用户是否点赞）
     * - ZSet递增/递减点赞数量（member为实体ID，score为点赞数）
     * - 异步更新业务表点赞数
     *
     * @param type   点赞类型
     * @param id     内容ID
     * @param userId 用户ID
     * @return true-点赞成功, false-取消点赞
     */
    @Override
    public boolean like(String type, Long id, Long userId) {
        String userKey = getUserKey(type, id);
        String countKey = getCountKey(type);

        // 检查用户是否已点赞（Set中是否存在）
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(userKey, userId.toString());

        if (isMember == null || !isMember) {
            // 未点赞，执行点赞操作
            // 1. 添加用户到Set
            stringRedisTemplate.opsForSet().add(userKey, userId.toString());
            // 2. ZSet点赞数+1，member为实体ID
            stringRedisTemplate.opsForZSet().incrementScore(countKey, id.toString(), 1);
            // 3. 发送RabbitMQ消息异步更新业务表点赞数
            rabbitTemplate.convertAndSend(RabbitMQConstants.THUMBS_UP_EXCHANGE, RabbitMQConstants.THUMBS_UP_ROUTING_KEY,
                    new ThumbsUpMessage(type, id, true));

            log.info("用户[{}]点赞了{}类型ID为[{}]的内容", userId, type, id);
            return true;
        } else {
            // 已点赞，执行取消点赞操作
            // 1. 从Set中移除用户
            stringRedisTemplate.opsForSet().remove(userKey, userId.toString());
            // 2. ZSet点赞数-1
            stringRedisTemplate.opsForZSet().incrementScore(countKey, id.toString(), -1);
            // 3. 发送RabbitMQ消息异步更新业务表点赞数
            rabbitTemplate.convertAndSend(RabbitMQConstants.THUMBS_UP_EXCHANGE, RabbitMQConstants.THUMBS_UP_ROUTING_KEY,
                    new ThumbsUpMessage(type, id, false));

            log.info("用户[{}]取消了{}类型ID为[{}]的内容的点赞", userId, type, id);
            return false;
        }
    }

    /**
     * 判断用户是否已点赞
     * 使用Set的isMember快速判断
     *
     * @param type   点赞类型
     * @param id     内容ID
     * @param userId 用户ID
     * @return true-已点赞, false-未点赞
     */
    @Override
    public boolean isLiked(String type, Long id, Long userId) {
        String userKey = getUserKey(type, id);
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(userKey, userId.toString());
        return isMember != null && isMember;
    }

    /**
     * 获取内容的点赞数
     * 从聚合ZSet中获取该实体ID作为member的score
     *
     * @param type 内容类型
     * @param id   内容ID
     * @return 点赞数
     */
    @Override
    public Long getLikedCount(String type, Long id) {
        String countKey = getCountKey(type);
        Double score = stringRedisTemplate.opsForZSet().score(countKey, id.toString());
        return score != null ? score.longValue() : 0L;
    }

    /**
     * 持久化点赞记录到数据库
     * 定时任务调用，将Redis中的点赞用户同步到blog_likes表
     *
     * @return 同步的记录数
     */
    @Override
    public int syncLikedCountToDb() {
        AtomicInteger syncCount = new AtomicInteger(0);

        // 持久化博客点赞记录
        syncCount.addAndGet(syncBlogLikes());

        // 持久化评论点赞记录
        syncCount.addAndGet(syncCommentLikes());

        log.info("点赞记录持久化完成，共同步{}条记录", syncCount.get());
        return syncCount.get();
    }

    /**
     * 持久化博客点赞记录
     *
     * @return 同步的记录数
     */
    private int syncBlogLikes() {
        AtomicInteger count = new AtomicInteger(0);
        Set<String> userKeys = stringRedisTemplate.keys(RedisConstants.BLOG_LIKED_USER_KEY + "*");

        if (userKeys != null && !userKeys.isEmpty()) {
            for (String userKey : userKeys) {
                try {
                    String blogIdStr = userKey.replace(RedisConstants.BLOG_LIKED_USER_KEY, "");
                    Long blogId = Long.parseLong(blogIdStr);

                    // 获取所有点赞用户
                    Set<String> members = stringRedisTemplate.opsForSet().members(userKey);
                    if (members != null) {
                        for (String userIdStr : members) {
                            Long userId = Long.parseLong(userIdStr);
                            thumbsUpRecordService.saveOrUpdateLikeRecord(ILikedService.TYPE_BLOG, blogId, userId, 1);
                            count.incrementAndGet();
                        }
                    }

                    // 从聚合ZSet获取点赞数并更新DB
                    String countKey = RedisConstants.BLOG_LIKED_COUNT_KEY;
                    Double score = stringRedisTemplate.opsForZSet().score(countKey, blogIdStr);
                    if (score != null && score > 0) {
                        thumbsUpRecordService.asyncUpdateLikeCount(ILikedService.TYPE_BLOG, blogId, true);
                    }

                    log.debug("持久化博客[{}]点赞记录: {}条", blogId, members != null ? members.size() : 0);
                } catch (Exception e) {
                    log.error("持久化博客点赞记录失败，key: {}", userKey, e);
                }
            }
        }
        return count.get();
    }

    /**
     * 持久化评论点赞记录
     *
     * @return 同步的记录数
     */
    private int syncCommentLikes() {
        AtomicInteger count = new AtomicInteger(0);
        Set<String> userKeys = stringRedisTemplate.keys(RedisConstants.COMMENT_LIKED_USER_KEY + "*");

        if (userKeys != null && !userKeys.isEmpty()) {
            for (String userKey : userKeys) {
                try {
                    String commentIdStr = userKey.replace(RedisConstants.COMMENT_LIKED_USER_KEY, "");
                    Long commentId = Long.parseLong(commentIdStr);

                    // 获取所有点赞用户
                    Set<String> members = stringRedisTemplate.opsForSet().members(userKey);
                    if (members != null) {
                        for (String userIdStr : members) {
                            Long userId = Long.parseLong(userIdStr);
                            thumbsUpRecordService.saveOrUpdateLikeRecord(ILikedService.TYPE_COMMENT, commentId, userId, 1);
                            count.incrementAndGet();
                        }
                    }

                    // 从聚合ZSet获取点赞数并更新DB
                    String countKey = RedisConstants.COMMENT_LIKED_COUNT_KEY;
                    Double score = stringRedisTemplate.opsForZSet().score(countKey, commentIdStr);
                    if (score != null && score > 0) {
                        thumbsUpRecordService.asyncUpdateLikeCount(ILikedService.TYPE_COMMENT, commentId, true);
                    }

                    log.debug("持久化评论[{}]点赞记录: {}条", commentId, members != null ? members.size() : 0);
                } catch (Exception e) {
                    log.error("持久化评论点赞记录失败，key: {}", userKey, e);
                }
            }
        }
        return count.get();
    }
}
