package com.wtlx.cancel;

/**
 * 生成取消异常
 * <p>
 * 在流式循环中检测到取消信号（本地标记 / 跨实例广播 / Redis 兜底标记）时抛出，
 * 由 Reactor 转换为 onError 并取消上游订阅，从而真正关闭模型侧流式连接。
 */
public class GenerationCancelledException extends RuntimeException {

    private final String requestId;

    public GenerationCancelledException(String requestId) {
        super("生成已取消: " + requestId);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
