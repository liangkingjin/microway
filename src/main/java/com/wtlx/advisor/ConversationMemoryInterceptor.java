package com.wtlx.advisor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.wtlx.config.ConversationContextHolder;
import com.wtlx.service.ConversationMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话记忆拦截器
 * 在 ReactAgent 每次调用模型前，自动加载对话上下文（摘要+历史）并注入到 prompt 中
 */
@Slf4j
@Component
public class ConversationMemoryInterceptor extends ModelInterceptor {

    /**
     * RunnableConfig 元数据中存放 conversationId 的 key
     * <p>
     * 流式执行时节点可能运行在 Reactor 线程上，ThreadLocal 无法传递，
     * 因此 conversationId 同时通过 RunnableConfig 元数据传递，保证多轮记忆不丢失。
     */
    public static final String CONVERSATION_ID_KEY = "conversationId";

    @Resource
    private ConversationMemoryService memoryService;

    @Override
    public String getName() {
        return "ConversationMemoryInterceptor";
    }

    /**
     * 解析当前会话 ID
     * 优先取 RunnableConfig 元数据（跨线程可靠），再回退到 ThreadLocal
     */
    private String resolveConversationId(ModelRequest request) {
        Map<String, Object> context = request.getContext();
        if (context != null) {
            Object value = context.get(CONVERSATION_ID_KEY);
            if (value instanceof String conversationId && !conversationId.isBlank()) {
                return conversationId;
            }
        }
        return ConversationContextHolder.getConversationId();
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 1. 优先从 RunnableConfig 元数据获取 conversationId，兜底使用 ThreadLocal
        String conversationId = resolveConversationId(request);

        if (conversationId == null || conversationId.isBlank()) {
            return handler.call(request);
        }

        try {
            // 2. 加载对话上下文（摘要 + 历史消息）
            List<Message> historyContext = memoryService.loadContext(conversationId);

            if (historyContext.isEmpty()) {
                return handler.call(request);
            }

            // 3. 提取摘要部分（SystemMessage）和历史消息
            StringBuilder summaryBuilder = new StringBuilder();
            List<Message> historyMessages = new ArrayList<>();

            for (Message msg : historyContext) {
                if (msg instanceof SystemMessage) {
                    summaryBuilder.append(msg.getText()).append("\n");
                } else {
                    historyMessages.add(msg);
                }
            }

            // 4. 合并摘要到现有系统消息中
            SystemMessage originalSystem = request.getSystemMessage();
            String originalText = (originalSystem != null) ? originalSystem.getText() : "";

            String enhancedSystem = originalText;
            if (!summaryBuilder.isEmpty()) {
                enhancedSystem = summaryBuilder.toString() + "\n" + originalText;
            }

            // 5. 合并历史消息到现有消息列表（历史在前，当前消息在后）
            List<Message> allMessages = new ArrayList<>(historyMessages);
            allMessages.addAll(request.getMessages());

            // 6. 构建新 request
            ModelRequest newRequest = ModelRequest.builder()
                    .systemMessage(new SystemMessage(enhancedSystem))
                    .messages(allMessages)
                    .options(request.getOptions())
                    .tools(request.getTools())
                    .dynamicToolCallbacks(request.getDynamicToolCallbacks())
                    .toolDescriptions(request.getToolDescriptions())
                    .context(request.getContext())
                    .build();

            log.debug("注入对话上下文, conversationId: {}, 摘要: {}, 历史消息: {}",
                    conversationId, !summaryBuilder.isEmpty(), historyMessages.size());

            return handler.call(newRequest);
        } catch (Exception e) {
            log.warn("加载对话上下文失败，继续执行: {}", e.getMessage());
            return handler.call(request);
        }
    }
}
