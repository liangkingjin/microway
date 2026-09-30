package com.wtlx.infrastructure.cancel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例正在运行的生成任务注册表
 * <p>
 * 只登记当前 JVM 持有的流；其他实例上的同名任务通过 Redis 广播 + 标记兜底命中。
 */
@Slf4j
@Component
public class GenerationRegistry {

    /**
     * requestId -> 取消句柄
     */
    private final ConcurrentHashMap<String, GenerationHandle> handles = new ConcurrentHashMap<>();

    /**
     * threadId -> 当前活跃 requestId（用于新流挤掉旧流）
     */
    private final ConcurrentHashMap<String, String> activeByThread = new ConcurrentHashMap<>();

    public void register(GenerationHandle handle) {
        handles.put(handle.getRequestId(), handle);
        activeByThread.put(handle.getThreadId(), handle.getRequestId());
    }

    public void unregister(String requestId) {
        GenerationHandle removed = handles.remove(requestId);
        if (removed != null) {
            activeByThread.remove(removed.getThreadId(), requestId);
        }
    }

    public GenerationHandle get(String requestId) {
        return requestId == null ? null : handles.get(requestId);
    }

    /**
     * 取消本地任务
     *
     * @return true 表示本实例持有该任务并已触发取消
     */
    public boolean cancel(String requestId) {
        GenerationHandle handle = get(requestId);
        if (handle == null) {
            return false;
        }
        boolean first = handle.cancel();
        log.info("本地取消生成, requestId: {}, threadId: {}, 首次取消: {}",
                requestId, handle.getThreadId(), first);
        return true;
    }

    /**
     * 查询会话下当前活跃的请求ID
     *
     * @param excludeRequestId 需要排除的请求ID（通常是发起查询的新流自身）
     */
    public String findActiveRequestId(String threadId, String excludeRequestId) {
        String active = threadId == null ? null : activeByThread.get(threadId);
        if (active == null || active.equals(excludeRequestId)) {
            return null;
        }
        return active;
    }

    public int size() {
        return handles.size();
    }
}
