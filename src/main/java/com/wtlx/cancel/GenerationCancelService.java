package com.wtlx.cancel;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * AI 生成取消服务（多实例）
 * <p>
 * 取消信号走两条路，互为补充：
 * <ol>
 *     <li><b>实时</b>：Redis Pub/Sub 广播 requestId，持有该流的实例在本地注册表命中后中断生成；</li>
 *     <li><b>兜底</b>：Redis 标记 key（带 TTL），流式循环低频检查。
 *         Pub/Sub 是 at-most-once，实例重启/网络抖动丢消息时仍能生效。</li>
 * </ol>
 * 取消请求可以打到任意实例：命中本地就直接取消，未命中则广播出去由持有者执行。
 */
@Slf4j
@Service
public class GenerationCancelService {

    /**
     * 取消广播频道
     */
    public static final String CANCEL_TOPIC = "ai:generation:cancel";

    /**
     * 取消标记 key 前缀（兜底检查用）
     */
    public static final String CANCEL_FLAG_PREFIX = "ai:generation:cancel:flag:";

    /**
     * 会话活跃流 key 前缀（threadId -> requestId，用于跨实例挤掉旧流）
     */
    public static final String ACTIVE_PREFIX = "ai:generation:active:";

    /**
     * 取消标记存活时间，覆盖广播丢失后的最长补偿窗口
     */
    private static final long FLAG_TTL_SECONDS = 60L;

    /**
     * 活跃流标记存活时间，需大于 SSE 超时时间
     */
    private static final long ACTIVE_TTL_SECONDS = 600L;

    /**
     * ID 白名单：只允许 UUID / 随机串，杜绝 Redis key 注入
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private GenerationRegistry registry;

    /**
     * 取消结果
     */
    public enum CancelStatus {
        /**
         * 本实例持有该流，已直接取消
         */
        CANCELLED,
        /**
         * 本实例无此流，已广播（其他实例可能命中，或该流已结束）
         */
        FORWARDED,
        /**
         * requestId 非法，拒绝处理
         */
        REJECTED
    }

    /**
     * 取消一次生成（可由任意实例接收）
     */
    public CancelStatus cancel(String requestId) {
        if (!isSafeId(requestId)) {
            log.warn("取消请求被拒绝，requestId 非法: {}", requestId);
            return CancelStatus.REJECTED;
        }
        boolean hitLocal = registry.cancel(requestId);
        markFlag(requestId);
        publish(requestId);
        return hitLocal ? CancelStatus.CANCELLED : CancelStatus.FORWARDED;
    }

    /**
     * 广播接收方调用：仅在本实例持有该流时才生效
     */
    public boolean cancelLocal(String requestId) {
        if (!isSafeId(requestId)) {
            return false;
        }
        return registry.cancel(requestId);
    }

    /**
     * 兜底检查：Redis 上是否已被置上取消标记
     */
    public boolean isMarked(String requestId) {
        if (!isSafeId(requestId)) {
            return false;
        }
        try {
            return flagBucket(requestId).isExists();
        } catch (Exception e) {
            log.warn("检查取消标记失败, requestId: {}", requestId, e);
            return false;
        }
    }

    /**
     * 新流启动时挤掉同一会话的旧流
     * <p>
     * 本地注册表优先；本地没有时查 Redis 活跃表，覆盖"旧流在别的实例"的情况。
     */
    public void cancelStaleByThreadId(String threadId, String currentRequestId) {
        if (!isSafeId(threadId) || !isSafeId(currentRequestId)) {
            return;
        }
        String stale = registry.findActiveRequestId(threadId, currentRequestId);
        if (stale == null) {
            stale = readActive(threadId);
            if (stale == null || stale.equals(currentRequestId)) {
                return;
            }
        }
        log.info("新流挤掉同会话旧流, threadId: {}, staleRequestId: {}", threadId, stale);
        cancel(stale);
    }

    /**
     * 登记会话当前活跃流（跨实例挤占的依据）
     */
    public void markActive(String threadId, String requestId) {
        if (!isSafeId(threadId) || !isSafeId(requestId)) {
            return;
        }
        try {
            activeBucket(threadId).set(requestId, ACTIVE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("登记活跃流失败, threadId: {}", threadId, e);
        }
    }

    /**
     * 流结束后清理活跃标记；只清理属于自己的标记，避免误删后继流
     */
    public void clearActive(String threadId, String requestId) {
        if (!isSafeId(threadId) || !isSafeId(requestId)) {
            return;
        }
        try {
            RBucket<String> bucket = activeBucket(threadId);
            if (requestId.equals(bucket.get())) {
                bucket.delete();
            }
        } catch (Exception e) {
            log.warn("清理活跃流失败, threadId: {}", threadId, e);
        }
    }

    private void markFlag(String requestId) {
        try {
            flagBucket(requestId).set(String.valueOf(System.currentTimeMillis()),
                    FLAG_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入取消标记失败, requestId: {}", requestId, e);
        }
    }

    private void publish(String requestId) {
        try {
            RTopic topic = redissonClient.getTopic(CANCEL_TOPIC, StringCodec.INSTANCE);
            topic.publish(requestId);
        } catch (Exception e) {
            log.warn("广播取消事件失败, requestId: {}（仍可依赖 Redis 标记兜底）", requestId, e);
        }
    }

    private String readActive(String threadId) {
        try {
            return activeBucket(threadId).get();
        } catch (Exception e) {
            log.warn("读取活跃流失败, threadId: {}", threadId, e);
            return null;
        }
    }

    private RBucket<String> flagBucket(String requestId) {
        return redissonClient.getBucket(CANCEL_FLAG_PREFIX + requestId, StringCodec.INSTANCE);
    }

    private RBucket<String> activeBucket(String threadId) {
        return redissonClient.getBucket(ACTIVE_PREFIX + threadId, StringCodec.INSTANCE);
    }

    /**
     * 校验 requestId / threadId，只允许安全字符，防止 Redis key 注入
     */
    public static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }
}
