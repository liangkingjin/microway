package com.wtlx.infrastructure.cancel;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

/**
 * 取消广播订阅器
 * <p>
 * 每个实例启动时订阅同一频道；收到 requestId 后只在本实例注册表命中时才动作，
 * 未命中说明流不在本实例上，静默忽略——这正是多实例下"谁持有谁执行"的判定点。
 */
@Slf4j
@Component
public class CancelBroadcastListener {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private GenerationCancelService cancelService;

    private RTopic topic;

    private int listenerId;

    @PostConstruct
    public void subscribe() {
        try {
            topic = redissonClient.getTopic(GenerationCancelService.CANCEL_TOPIC, StringCodec.INSTANCE);
            listenerId = topic.addListener(String.class, (channel, requestId) -> {
                if (requestId == null || requestId.isBlank()) {
                    return;
                }
                if (cancelService.cancelLocal(requestId)) {
                    log.info("收到跨实例取消广播并命中本地流, requestId: {}", requestId);
                }
            });
            log.info("已订阅 AI 生成取消广播频道: {}", GenerationCancelService.CANCEL_TOPIC);
        } catch (Exception e) {
            log.error("订阅取消广播失败，跨实例取消将退化为 Redis 标记兜底", e);
        }
    }

    @PreDestroy
    public void unsubscribe() {
        if (topic != null) {
            try {
                topic.removeListener(listenerId);
            } catch (Exception e) {
                log.warn("移除取消广播监听失败", e);
            }
        }
    }
}
