package com.wtlx.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 对话编排服务（Web 层门面）。
 * <p>
 * 承担原 {@code AiController} 中的流程性逻辑：会话 / 请求 id 兜底、登录用户解析、
 * 历史消息组装与卡片解析。Controller 因此只保留"收请求 → 调服务 → 装结果"。
 *
 * @author 金小梁
 */
public interface AiChatService {

    /**
     * 发起一次流式对话
     *
     * @param message   用户消息
     * @param threadId  会话线程 id；为空时由服务端生成
     * @param requestId 本次生成请求 id；为空时由服务端生成
     * @return SSE 流
     */
    SseEmitter chat(String message, String threadId, String requestId);

    /**
     * 取消一次正在进行的生成（可打到任意实例，幂等）
     *
     * @param requestId 生成请求 id
     * @return {@code requestId} + {@code status}（cancelled / forwarded / rejected）
     */
    Map<String, Object> cancel(String requestId);

    /**
     * 获取对话历史（含检索卡片，供前端恢复"正文 + 卡片"混合展示）
     *
     * @param threadId 会话线程 id
     * @return {@code threadId} + {@code messages}
     */
    Map<String, Object> history(String threadId);

    /**
     * 清除对话历史
     *
     * @param threadId 会话线程 id
     */
    void clearHistory(String threadId);
}
