package com.wtlx.controller;

import com.wtlx.service.AiChatService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 旅游规划智能体接口（新链路）。
 * <p>
 * 本类只负责接收请求与转发：会话 / 请求 id 兜底、身份解析、历史组装等业务逻辑
 * 集中在 {@link AiChatService}，Controller 不再承载任何流程代码。
 *
 * @author 金小梁
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiChatService aiChatService;

    /**
     * 流式调用基于 SpringAI Alibaba ReactAgent 的旅游规划智能体
     *
     * @param message   用户消息
     * @param threadId  会话线程ID（可选），用于短期记忆隔离
     * @param requestId 本次生成请求ID（可选），由前端生成，用于取消这条流；缺省时服务端补一个
     * @return SSE 流
     */
    @GetMapping(value = "/react/trip/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter doChatWithReactAgent(@RequestParam String message,
                                           @RequestParam(required = false) String threadId,
                                           @RequestParam(required = false) String requestId) {
        return aiChatService.chat(message, threadId, requestId);
    }

    /**
     * 取消一次正在进行的生成（可打到任意实例）
     *
     * @param requestId 生成请求ID
     * @return cancelled=本地命中并已取消；forwarded=已广播；rejected=ID 非法
     */
    @PostMapping("/react/cancel")
    public Map<String, Object> cancelGeneration(@RequestParam String requestId) {
        return aiChatService.cancel(requestId);
    }

    /**
     * 获取 ReactAgent 聊天历史（摘要 + 消息）
     *
     * @param threadId 会话线程ID
     * @return 包含 threadId 和 context 的历史数据
     */
    @GetMapping("/react/history")
    public Map<String, Object> getReactAgentHistory(@RequestParam String threadId) {
        return aiChatService.history(threadId);
    }

    /**
     * 清除 ReactAgent 聊天历史
     *
     * @param threadId 会话线程ID
     */
    @DeleteMapping("/react/history")
    public String clearReactAgentHistory(@RequestParam String threadId) {
        aiChatService.clearHistory(threadId);
        return "ReactAgent 历史已清除";
    }
}
