package com.wtlx.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.wtlx.advisor.ConversationMemoryInterceptor;
import com.wtlx.config.ConversationContextHolder;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.TripAgentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 旅游规划 Agent 服务实现
 * 基于 SpringAI Alibaba ReactAgent 实现
 * <p>
 * 流式输出三个事件：
 * <ul>
 *     <li>{@code event: delta} — 模型增量文本，边生成边推送</li>
 *     <li>{@code event: done}  — 结束，携带本轮完整回答</li>
 *     <li>{@code event: error} — 异常信息</li>
 * </ul>
 * 取消机制：客户端关闭连接（或超时）时，服务端检测到后中断 Flux 订阅，
 * Reactor 会取消上游、关闭模型流式连接，已生成内容正常落库。
 * 由于取消信号走的是当前连接本身，天然落在持有该流的实例上，无需跨实例通信。
 */
@Slf4j
@Service
public class TripAgentServiceImpl implements TripAgentService {

    /**
     * SSE 超时时间：5 分钟
     */
    private static final long SSE_TIMEOUT = 300000L;

    @Resource
    private ReactAgent tripReactAgent;

    @Resource
    private ConversationMemoryService memoryService;

    @Override
    public SseEmitter chatStream(String message, String threadId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String conversationId = "react_agent_" + threadId;

        // 取消标记：客户端断开连接 / 超时时置为 true，流式循环检测到后中断生成
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            log.warn("SSE 连接超时, threadId: {}", threadId);
        });

        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            try {
                ConversationContextHolder.setConversationId(conversationId);

                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        // 流式执行跑在 Reactor 线程，ThreadLocal 传不过去，故同时放进元数据
                        .addMetadata(ConversationMemoryInterceptor.CONVERSATION_ID_KEY, conversationId)
                        .build();

                // 逐块消费流式输出，每来一块就推给前端
                tripReactAgent.stream(message, config)
                        .doOnNext(output -> {
                            // 客户端已断开：抛异常中断 Flux，Reactor 会取消上游订阅、关闭模型流
                            if (cancelled.get()) {
                                throw new IllegalStateException("客户端已断开，取消生成");
                            }
                            String chunk = extractChunk(output);
                            if (chunk != null) {
                                answer.append(chunk);
                                if (!send(emitter, "delta", chunk)) {
                                    cancelled.set(true);
                                    throw new IllegalStateException("客户端已断开，取消生成");
                                }
                            }
                        })
                        .blockLast();

                send(emitter, "done", answer.toString());
            } catch (Exception e) {
                if (cancelled.get()) {
                    // 取消属于正常行为：已生成的内容照常落库
                    log.info("生成已取消（客户端断开）, threadId: {}, 已生成 {} 字", threadId, answer.length());
                } else {
                    log.error("ReactAgent 调用失败, threadId: {}", threadId, e);
                    send(emitter, "error", "Agent 执行错误: " + e.getMessage());
                }
            } finally {
                if (!answer.isEmpty()) {
                    memoryService.appendAndCompressIfNeeded(conversationId, message, answer.toString());
                }
                ConversationContextHolder.clear();
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 发送事件；连接已断开时返回 false，由调用方决定是否取消生成
     */
    private boolean send(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (Exception e) {
            log.debug("SSE 事件发送失败, event: {}, 原因: {}", eventName, e.getMessage());
            return false;
        }
    }

    /**
     * 取出模型增量文本；非增量输出（工具调用块、结束块等）返回 null
     */
    private String extractChunk(Object nodeOutput) {
        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)
                || streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return null;
        }
        Message message = streamingOutput.message();
        if (message == null || message.getText() == null || message.getText().isEmpty()) {
            return null;
        }
        return message.getText();
    }
}
