package com.wtlx.ai.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 旅游规划 Agent 服务接口
 * 基于 SpringAI Alibaba ReactAgent 实现
 */
public interface TripAgentService {

    /**
     * 流式调用 ReactAgent 旅游规划
     *
     * @param message  用户消息
     * @param threadId 会话线程ID（用于记忆隔离）
     * @return SSE 流
     */
    SseEmitter chatStream(String message, String threadId);
}
