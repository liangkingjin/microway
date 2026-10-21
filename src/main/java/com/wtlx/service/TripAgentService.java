package com.wtlx.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 旅游规划 Agent 服务接口
 * 基于 SpringAI Alibaba ReactAgent 实现
 */
public interface TripAgentService {

    /**
     * 流式调用 ReactAgent 旅游规划
     *
     * @param message   用户消息
     * @param threadId  会话线程ID（短期记忆隔离维度）
     * @param requestId 本次生成请求ID（用于精确定位并取消这条流，多实例下由前端生成后透传）
     * @param userId    记忆作用域（长期记忆隔离维度），即当前登录用户 id；
     *                  为 null 时仅启用会话内短期记忆
     * @return SSE 流
     */
    SseEmitter chatStream(String message, String threadId, String requestId, Long userId);
}
