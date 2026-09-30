package com.wtlx.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.advisor.ConversationMemoryInterceptor;
import com.wtlx.agent.StreamEventRouter;
import com.wtlx.cancel.GenerationCancelService;
import com.wtlx.cancel.GenerationCancelledException;
import com.wtlx.cancel.GenerationHandle;
import com.wtlx.cancel.GenerationRegistry;
import com.wtlx.config.ConversationContextHolder;
import com.wtlx.constant.RagDocType;
import com.wtlx.entity.dto.ai.ThinkingStepVO;
import com.wtlx.entity.dto.ai.TripAnswerVO;
import com.wtlx.entity.dto.ai.TripCardVO;
import com.wtlx.rag.RagCardAssembler;
import com.wtlx.rag.RagHit;
import com.wtlx.rag.RagProperties;
import com.wtlx.rag.RagSearchService;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.TripAgentService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 输出形态：结论正文与思考过程分离。
 * <ul>
 *     <li>{@code event: thinking} — 思考步骤（工具调用 / 检索 / 推理），JSON，按 seq 幂等覆盖</li>
 *     <li>{@code event: delta}    — 结论正文增量，边生成边推送</li>
 *     <li>{@code event: cards}    — 关联游记卡片，JSON 数组；未检索到则不发</li>
 *     <li>{@code event: done}     — 正常结束，JSON，携带 content / cards / thinking</li>
 *     <li>{@code event: cancelled}— 被取消（用户停止 / 断开 / 新流挤占），JSON，携带已生成部分</li>
 *     <li>{@code event: error}    — 异常信息</li>
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

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagCardAssembler ragCardAssembler;

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
            List<ThinkingStepVO> steps = new ArrayList<>();
            Map<Integer, ThinkingStepVO> stepIndex = new HashMap<>();
            AtomicReference<List<TripCardVO>> cardsRef = new AtomicReference<>(List.of());
            AtomicReference<SignalType> signalRef = new AtomicReference<>();
            try {
                ConversationContextHolder.setConversationId(conversationId);

                RunnableConfig config = RunnableConfig.builder()
                        .threadId(threadId)
                        // 流式执行跑在 Reactor 线程，ThreadLocal 传不过去，故同时放进元数据
                        .addMetadata(ConversationMemoryInterceptor.CONVERSATION_ID_KEY, conversationId)
                        .build();

                StreamEventRouter router = new StreamEventRouter(new StreamEventRouter.StreamSink() {
                    @Override
                    public void onThinking(ThinkingStepVO step) {
                        ThinkingStepVO existed = stepIndex.get(step.getSeq());
                        if (existed == null) {
                            steps.add(step);
                            stepIndex.put(step.getSeq(), step);
                        } else {
                            existed.setLabel(step.getLabel());
                            existed.setText(step.getText());
                        }
                        sendJson(emitter, "thinking", step);
                    }

                    @Override
                    public void onContent(String chunk) {
                        answer.append(chunk);
                        if (!send(emitter, "delta", chunk)) {
                            cancelled.set(true);
                            throw new GenerationCancelledException(requestId);
                        }
                    }
                });

                // 会话级检索：命中站内内容才产出卡片，未命中则不发 cards 事件
                List<TripCardVO> cards = resolveCards(message, router, emitter);
                cardsRef.set(cards);

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
                            router.accept(output);
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
                router.finish();

                TripAnswerVO payload = TripAnswerVO.builder()
                        .content(answer.toString())
                        .cards(cards)
                        .thinking(steps)
                        .build();

                if (signalRef.get() == SignalType.CANCEL) {
                    log.info("生成已取消, threadId: {}, requestId: {}, 已生成 {} 字",
                            threadId, requestId, answer.length());
                    sendJson(emitter, "cancelled", payload);
                } else {
                    sendJson(emitter, "done", payload);
                }
            } catch (GenerationCancelledException e) {
                log.info("生成已取消, threadId: {}, requestId: {}, 已生成 {} 字",
                        threadId, requestId, answer.length());
                sendJson(emitter, "cancelled", buildPayload(answer.toString(), steps, cardsRef.get()));
            } catch (Exception e) {
                if (cancelled.get()) {
                    log.info("生成已取消（客户端断开）, requestId: {}, 已生成 {} 字", requestId, answer.length());
                    sendJson(emitter, "cancelled", buildPayload(answer.toString(), steps, cardsRef.get()));
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

    /**
     * 解析本轮关联的游记卡片。
     * <p>
     * 由后端按用户原始问题做一次相似度检索（不依赖模型输出 id），命中则发 {@code cards} 事件；
     * 未命中或检索异常时返回空集合，前端据此不渲染卡片区。
     *
     * @param message 用户消息
     * @param router  分流器，用于记录"检索站内游记"这一思考步骤
     * @param emitter SSE 通道，用于推送 cards 事件
     * @return 卡片列表，可能为 empty 但绝不为 null
     */
    private List<TripCardVO> resolveCards(String message, StreamEventRouter router, SseEmitter emitter) {
        if (ragProperties == null || !ragProperties.isEnabled()) {
            return List.of();
        }
        try {
            router.addStep("retrieval", "检索站内游记与攻略", message);
            List<RagHit> hits = ragSearchService.search(message, ragProperties.getCardTopK(),
                    ragProperties.getCardThreshold(), List.of(RagDocType.BLOG, RagDocType.GUIDE));
            List<TripCardVO> cards = ragCardAssembler.assemble(hits);
            if (!cards.isEmpty()) {
                sendJson(emitter, "cards", cards);
            }
            router.addStep("retrieval", "检索结果",
                    cards.isEmpty() ? "未命中相关内容" : "命中 " + cards.size() + " 条相关内容");
            return cards;
        } catch (Exception e) {
            log.warn("会话级检索失败，本次不展示卡片: {}", e.getMessage());
            return List.of();
        }
    }

    private TripAnswerVO buildPayload(String content, List<ThinkingStepVO> steps, List<TripCardVO> cards) {
        return TripAnswerVO.builder()
                .content(content)
                .cards(cards == null ? List.of() : cards)
                .thinking(steps)
                .build();
    }

    @PreDestroy
    public void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    /**
     * 发送文本事件；连接已断开时返回 false，由调用方决定是否取消生成
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
     * 发送 JSON 事件；序列化失败或连接断开时不影响主流程
     */
    private boolean sendJson(SseEmitter emitter, String eventName, Object payload) {
        try {
            return send(emitter, eventName, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("SSE JSON 事件序列化失败, event: {}", eventName, e);
            return false;
        }
    }
}
