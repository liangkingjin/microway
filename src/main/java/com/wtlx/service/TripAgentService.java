package com.wtlx.service;

import com.wtlx.infrastructure.memory.support.MemoryPrincipal;
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
     * @param principal 记忆主体（长期记忆隔离维度）；无身份线索时传 {@link MemoryPrincipal#NONE}，
     *                  此时仅启用会话内短期记忆
     * @return SSE 流
     */
    SseEmitter chatStream(String message, String threadId, String requestId, MemoryPrincipal principal);
}
