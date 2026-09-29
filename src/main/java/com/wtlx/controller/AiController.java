package com.wtlx.controller;

import com.wtlx.entity.conversation.enums.MessageRole;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.TripAgentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * <p>
 * 基于 SpringAI Alibaba ReactAgent，老链路接口已归档至 {@link LegacyTripApi}。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private TripAgentService tripAgentService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    /**
     * 流式调用基于 SpringAI Alibaba ReactAgent 的旅游规划智能体
     *
     * @param message  用户消息
     * @param threadId 会话线程ID（可选），用于记忆隔离
     * @return SSE 流
     */
    @GetMapping(value = "/react/trip/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter doChatWithReactAgent(@RequestParam String message,
                                           @RequestParam(required = false) String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            threadId = UUID.randomUUID().toString();
        }
        return tripAgentService.chatStream(message, threadId);
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
