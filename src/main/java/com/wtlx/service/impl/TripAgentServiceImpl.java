package com.wtlx.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.wtlx.advisor.ConversationMemoryInterceptor;
import com.wtlx.cancel.GenerationCancelService;
import com.wtlx.cancel.GenerationCancelledException;
import com.wtlx.cancel.GenerationHandle;
import com.wtlx.cancel.GenerationRegistry;
import com.wtlx.config.ConversationContextHolder;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.TripAgentService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.SignalType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 旅游规划 Agent 服务实现
 * 基于 SpringAI Alibaba ReactAgent 实现
 * <p>
 * 流式输出四个事件：
 * <ul>
 *     <li>{@code event: delta} — 模型增量文本，边生成边推送</li>
 *     <li>{@code event: done}  — 正常结束，携带本轮完整回答</li>
 *     <li>{@code event: cancelled} — 被取消（用户停止 / 断开 / 新流挤占），携带已生成部分</li>
 *     <li>{@code event: error} — 异常信息</li>
 * </ul>
 * 取消机制（多实例）：
 * <ol>
 *     <li>取消请求可打到任意实例，命中本地注册表则直接中断，否则经 Redis 广播给持有者；</li>
 *     <li>中断手段是 dispose 掉 Flux 订阅，由 Reactor 关闭模型侧流式连接，而不是等下一块文本；</li>
 *     <li>Redis 标记 key 作为 Pub/Sub 丢消息时的兜底，流式循环每 2 秒检查一次；</li>
 *     <li>已生成内容照常落库，前端按 cancelled 事件标记"已停止"。</li>
 * </ol>
 */
@Slf4j
@Service
public class TripAgentServiceImpl implements TripAgentService {

    /**
     * SSE 超时时间：5 分钟
     */
    private static final long SSE_TIMEOUT = 300000L;

    /**
     * Redis 取消标记的兜底检查间隔
     */
    private static final long CANCEL_CHECK_INTERVAL_MS = 2000L;

    /**
     * SSE 心跳间隔：保持连接活跃，并在客户端断开时尽快触发取消
     */
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;

    @Resource
    private ReactAgent tripReactAgent;

    @Resource
    private ConversationMemoryService memoryService;

    @Resource
    private GenerationRegistry generationRegistry;

    @Resource
    private GenerationCancelService cancelService;

    /**
     * 心跳调度线程池（全实例共享）
     */
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newScheduledThreadPool(2, r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("sse-heartbeat-" + thread.threadId());
                return thread;
            });

    @Override
    public SseEmitter chatStream(String message, String threadId, String requestId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String conversationId = "react_agent_" + threadId;

        // 1. 新流挤掉同会话的旧流（用户连点时旧流可能在别的实例上，走 Redis 活跃表）
        cancelService.cancelStaleByThreadId(threadId, requestId);

        // 2. 登记取消句柄：取消动作 = 置标记 + dispose 掉 Flux 订阅
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        GenerationHandle handle = new GenerationHandle(requestId, threadId);
        handle.bindCancelAction(() -> {
            cancelled.set(true);
            Disposable disposable = disposableRef.get();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        });
        generationRegistry.register(handle);
        cancelService.markActive(threadId, requestId);

        // 3. 客户端断开 / 超时 = 取消
        emitter.onCompletion(() -> cancelService.cancelLocal(requestId));
        emitter.onError(e -> cancelService.cancelLocal(requestId));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时, threadId: {}, requestId: {}", threadId, requestId);
            cancelService.cancelLocal(requestId);
        });

        // 4. 心跳：既保活，也能在没有文本产出时尽快感知断开
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (cancelled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.debug("SSE 心跳发送失败，判定客户端已断开, requestId: {}", requestId);
                cancelService.cancelLocal(requestId);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        CompletableFuture.runAsync(() -> {
            StringBuilder answer = new StringBuilder();
            AtomicReference<SignalType> signalRef = new AtomicReference<>();
            try {
                ConversationContextHolder.setConversationId(conversationId);

                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        // 流式执行跑在 Reactor 线程，ThreadLocal 传不过去，故同时放进元数据
                        .addMetadata(ConversationMemoryInterceptor.CONVERSATION_ID_KEY, conversationId)
                        .build();

                CountDownLatch finishLatch = new CountDownLatch(1);
                long[] lastCheckAt = {System.currentTimeMillis()};

                Disposable disposable = tripReactAgent.stream(message, config)
                        .doOnNext(output -> {
                            // 已被取消：抛异常中断 Flux，Reactor 会取消上游、关闭模型流
                            if (cancelled.get()) {
                                throw new GenerationCancelledException(requestId);
                            }
                            // 兜底：低频检查 Redis 标记，覆盖 Pub/Sub 丢消息的情况
                            long now = System.currentTimeMillis();
                            if (now - lastCheckAt[0] >= CANCEL_CHECK_INTERVAL_MS) {
                                lastCheckAt[0] = now;
                                if (cancelService.isMarked(requestId)) {
                                    cancelled.set(true);
                                    throw new GenerationCancelledException(requestId);
                                }
                            }
                            String chunk = extractChunk(output);
                            if (chunk != null) {
                                answer.append(chunk);
                                if (!send(emitter, "delta", chunk)) {
                                    cancelled.set(true);
                                    throw new GenerationCancelledException(requestId);
                                }
                            }
                        })
                        .doFinally(signal -> {
                            signalRef.set(signal);
                            finishLatch.countDown();
                        })
                        .subscribe(
                                output -> {
                                },
                                error -> {
                                    if (!(error instanceof GenerationCancelledException)) {
                                        log.error("ReactAgent 流式执行异常, requestId: {}", requestId, error);
                                    }
                                },
                                finishLatch::countDown
                        );
                // 订阅句柄：外部取消时直接 dispose，由 Reactor 关闭模型侧流式连接
                disposableRef.set(disposable);

                // subscribe 是异步的，这里等流结束再收尾（与原先 blockLast 的阻塞行为一致）
                finishLatch.await(SSE_TIMEOUT, TimeUnit.MILLISECONDS);

                if (signalRef.get() == SignalType.CANCEL) {
                    log.info("生成已取消, threadId: {}, requestId: {}, 已生成 {} 字",
                            threadId, requestId, answer.length());
                    send(emitter, "cancelled", answer.toString());
                } else {
                    send(emitter, "done", answer.toString());
                }
            } catch (GenerationCancelledException e) {
                log.info("生成已取消, threadId: {}, requestId: {}, 已生成 {} 字",
                        threadId, requestId, answer.length());
                send(emitter, "cancelled", answer.toString());
            } catch (Exception e) {
                if (cancelled.get()) {
                    log.info("生成已取消（客户端断开）, requestId: {}, 已生成 {} 字", requestId, answer.length());
                    send(emitter, "cancelled", answer.toString());
                } else {
                    log.error("ReactAgent 调用失败, threadId: {}, requestId: {}", threadId, requestId, e);
                    send(emitter, "error", "Agent 执行错误: " + e.getMessage());
                }
            } finally {
                if (!answer.isEmpty()) {
                    memoryService.appendAndCompressIfNeeded(conversationId, message, answer.toString());
                }
                ConversationContextHolder.clear();
                generationRegistry.unregister(requestId);
                cancelService.clearActive(threadId, requestId);
                heartbeat.cancel(false);
                emitter.complete();
            }
        });

        return emitter;
    }

    @PreDestroy
    public void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
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
