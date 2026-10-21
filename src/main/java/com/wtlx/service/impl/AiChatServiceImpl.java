package com.wtlx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.conversation.enums.MessageRole;
import com.wtlx.domain.entity.dto.ai.TripCardVO;
import com.wtlx.infrastructure.cancel.GenerationCancelService;
import com.wtlx.service.AiChatService;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.TripAgentService;
import com.wtlx.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 对话编排服务实现。
 * <p>
 * 这里承接的是原先散落在 Controller 里的流程性逻辑，保持接口行为不变：
 * <ul>
 *     <li>{@link #chat}：threadId / requestId 为空时补 UUID，并在<b>请求线程</b>解析登录用户 id
 *         （Reactor 线程读不到 ThreadLocal，故必须在进入异步链路前取好）；</li>
 *     <li>{@link #cancel}：把取消结果封装为前端约定的响应体；</li>
 *     <li>{@link #history} / {@link #clearHistory}：会话 id 派生、历史读取、角色过滤与卡片解析。</li>
 * </ul>
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    /**
     * 前端展示历史时最多回溯的消息条数（用户 + 助手合计）
     */
    private static final int HISTORY_DISPLAY_LIMIT = 100;

    /**
     * 会话 id 前缀：与长期记忆 / 历史读取共用同一派生规则
     */
    private static final String CONVERSATION_PREFIX = "react_agent_";

    @Resource
    private TripAgentService tripAgentService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource
    private GenerationCancelService generationCancelService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public SseEmitter chat(String message, String threadId, String requestId) {
        String effectiveThreadId = (threadId == null || threadId.isEmpty())
                ? UUID.randomUUID().toString()
                : threadId;
        String effectiveRequestId = (requestId == null || requestId.isEmpty())
                ? UUID.randomUUID().toString()
                : requestId;
        // 身份只能在请求线程取（ThreadLocal），下游 Reactor 线程靠 RunnableConfig 元数据传递。
        // '/ai/**' 已要求登录，故这里必然拿到真实用户
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        return tripAgentService.chatStream(message, effectiveThreadId, effectiveRequestId, userId);
    }

    @Override
    public Map<String, Object> cancel(String requestId) {
        GenerationCancelService.CancelStatus status = generationCancelService.cancel(requestId);
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("status", status.name().toLowerCase());
        return result;
    }

    @Override
    public Map<String, Object> history(String threadId) {
        String conversationId = CONVERSATION_PREFIX + threadId;

        // 展示用历史：含检索卡片，供重新进入会话时恢复"正文 + 游记卡片"混合展示。
        // 与模型上下文（context）分开取，避免卡片 JSON 混入送给模型的消息里。
        List<ConversationMessage> recent = conversationMemoryService.loadRecentMessages(
                conversationId, HISTORY_DISPLAY_LIMIT);

        Map<String, Object> result = new HashMap<>();
        result.put("threadId", threadId);
        result.put("messages", recent.stream()
                .filter(msg -> msg.getRole() == MessageRole.USER || msg.getRole() == MessageRole.ASSISTANT)
                .map(msg -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("role", msg.getRole().getCode());
                    item.put("content", msg.getContent());
                    item.put("cards", parseCards(msg.getCardsJson()));
                    item.put("time", msg.getCreatedAt() == null ? null : msg.getCreatedAt().toString());
                    return item;
                })
                .toList());
        return result;
    }

    @Override
    public void clearHistory(String threadId) {
        conversationMemoryService.clear(CONVERSATION_PREFIX + threadId);
    }

    /**
     * 解析历史消息里附带的卡片 JSON，缺失或非法时返回空集合
     */
    private List<TripCardVO> parseCards(String cardsJson) {
        if (cardsJson == null || cardsJson.isBlank()) {
            return List.of();
        }
        try {
            TripCardVO[] cards = objectMapper.readValue(cardsJson, TripCardVO[].class);
            return cards == null ? List.of() : List.of(cards);
        } catch (Exception e) {
            log.warn("历史消息卡片解析失败，按无卡片处理: {}", e.getMessage());
            return List.of();
        }
    }
}
