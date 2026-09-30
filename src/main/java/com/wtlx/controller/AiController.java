package com.wtlx.controller;

import com.wtlx.infrastructure.cancel.GenerationCancelService;
import com.wtlx.domain.entity.conversation.enums.MessageRole;
import com.wtlx.application.conversation.ConversationMemoryService;
import com.wtlx.application.trip.TripAgentService;
import jakarta.annotation.Resource;
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
public class AiController {

    @Resource
    private TripAgentService tripAgentService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource
    private GenerationCancelService generationCancelService;

    /**
     * 流式调用基于 SpringAI Alibaba ReactAgent 的旅游规划智能体
     *
     * @param message   用户消息
     * @param threadId  会话线程ID（可选），用于记忆隔离
     * @param requestId 本次生成请求ID（可选），由前端生成，用于取消这条流；缺省时服务端补一个
     * @return SSE 流
     */
    @GetMapping(value = "/react/trip/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter doChatWithReactAgent(@RequestParam String message,
                                           @RequestParam(required = false) String threadId,
                                           @RequestParam(required = false) String requestId) {
        if (threadId == null || threadId.isEmpty()) {
            threadId = UUID.randomUUID().toString();
        }
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return tripAgentService.chatStream(message, threadId, requestId);
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
        List<org.springframework.ai.chat.messages.Message> context =
                conversationMemoryService.loadContext(conversationId);

        Map<String, Object> result = new HashMap<>();
        result.put("threadId", threadId);
        result.put("context", context.stream().map(msg -> {
            Map<String, String> item = new HashMap<>();
            item.put("role", MessageRole.fromMessageType(msg.getMessageType()).getCode());
            item.put("content", msg.getText());
            return item;
        }).toList());

        return result;
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
