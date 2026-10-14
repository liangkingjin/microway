package com.wtlx.controller;

import com.wtlx.infrastructure.cancel.GenerationCancelService;
import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.conversation.enums.MessageRole;
import com.wtlx.domain.entity.dto.ai.TripCardVO;
import com.wtlx.infrastructure.memory.support.MemoryPrincipal;
import com.wtlx.infrastructure.memory.support.MemorySubjectResolver;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.TripAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 旅游规划智能体接口（新链路）
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    /**
     * 前端展示历史时最多回溯的消息条数（用户+助手合计）
     */
    private static final int HISTORY_DISPLAY_LIMIT = 100;

    @Resource
    private TripAgentService tripAgentService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource
    private GenerationCancelService generationCancelService;

    @Resource
    private MemorySubjectResolver memorySubjectResolver;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 流式调用基于 SpringAI Alibaba ReactAgent 的旅游规划智能体
     *
     * @param message   用户消息
     * @param threadId  会话线程ID（可选），用于记忆隔离
     * @param requestId 本次生成请求ID（可选），由前端生成，用于取消这条流；缺省时服务端补一个
     * @param userId    用户ID（可选），显式指定长期记忆主体
     * @param token     登录 token（可选）；{@code /ai/**} 免登录且 EventSource 无法带请求头，
     *                  因此约定用查询参数透传，服务端回查 Redis 登录态解析出用户身份
     * @param clientId  匿名客户端ID（可选），前端 localStorage 持久化，用于游客的跨会话记忆
     * @return SSE 流
     */
    @GetMapping(value = "/react/trip/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter doChatWithReactAgent(@RequestParam String message,
                                           @RequestParam(required = false) String threadId,
                                           @RequestParam(required = false) String requestId,
                                           @RequestParam(required = false) String userId,
                                           @RequestParam(required = false) String token,
                                           @RequestParam(required = false) String clientId) {
        if (threadId == null || threadId.isEmpty()) {
            threadId = UUID.randomUUID().toString();
        }
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        // 记忆主体在控制层解析：身份线索的获取是 Web 层职责，服务层只消费结果
        MemoryPrincipal principal = memorySubjectResolver.resolve(userId, token, clientId);
        return tripAgentService.chatStream(message, threadId, requestId, principal);
    }

    /**
     * 取消一次正在进行的生成（可打到任意实例）
     * <p>
     * 请求落到哪台机器都无所谓：本地有这条流就直接中断，没有就经 Redis 广播给持有者，
     * 同时写标记 key 做兜底。重复调用幂等。
     *
     * @param requestId 生成请求ID
     * @return cancelled=本地命中并已取消；forwarded=已广播（流可能在其他实例或已结束）；rejected=ID 非法
     */
    @PostMapping("/react/cancel")
    public Map<String, Object> cancelGeneration(@RequestParam String requestId) {
        GenerationCancelService.CancelStatus status = generationCancelService.cancel(requestId);
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("status", status.name().toLowerCase());
        return result;
    }

    /**
     * 获取 ReactAgent 聊天历史（摘要 + 消息）
     *
     * @param threadId 会话线程ID
     * @return 包含 threadId 和 context 的历史数据
     */
    @GetMapping("/react/history")
    public Map<String, Object> getReactAgentHistory(@RequestParam String threadId) {
        String conversationId = "react_agent_" + threadId;

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

    /**
     * 清除 ReactAgent 聊天历史
     *
     * @param threadId 会话线程ID
     */
    @DeleteMapping("/react/history")
    public String clearReactAgentHistory(@RequestParam String threadId) {
        String conversationId = "react_agent_" + threadId;
        conversationMemoryService.clear(conversationId);
        return "ReactAgent 历史已清除";
    }
}
