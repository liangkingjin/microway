package com.wtlx.infrastructure.cancel;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次生成的取消句柄
 * <p>
 * 持有取消标记与取消动作（{@link reactor.core.Disposable#dispose()} 等），
 * 保证 cancel() 幂等：重复取消只有第一次会真正执行动作。
 */
@Slf4j
@Getter
public class GenerationHandle {

    private final String requestId;

    private final String threadId;

    private final long createTime = System.currentTimeMillis();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 取消动作由流式任务绑定：置标记 + 中断 Flux 订阅
     */
    private volatile Runnable cancelAction;

    public GenerationHandle(String requestId, String threadId) {
        this.requestId = requestId;
        this.threadId = threadId;
    }

    /**
     * 绑定取消动作
     * <p>
     * 若绑定前已被取消（注册与取消广播的竞态），立即补触发一次，避免取消信号丢失。
     */
    public synchronized void bindCancelAction(Runnable action) {
        this.cancelAction = action;
        if (cancelled.get()) {
            runCancelAction();
        }
    }

    /**
     * 取消本次生成，幂等
     *
     * @return true 表示本次调用是首次取消
     */
    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        runCancelAction();
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    private void runCancelAction() {
        Runnable action = this.cancelAction;
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            log.warn("执行取消动作失败, requestId: {}", requestId, e);
        }
    }
}
