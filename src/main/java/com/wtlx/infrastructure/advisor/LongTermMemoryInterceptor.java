package com.wtlx.infrastructure.advisor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.wtlx.infrastructure.memory.context.MemoryContextBuilder;
import com.wtlx.infrastructure.memory.support.MemoryConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆注入拦截器（跨会话）。
 * <p>
 * 与 {@link ConversationMemoryInterceptor}（会话内短期记忆）的分工是明确的：
 * <ul>
 *     <li>短期记忆回答"<b>这一轮在聊什么</b>"——摘要 + 最近若干轮原文，作用域 {@code conversationId}，
 *         会话结束即失去价值；</li>
 *     <li>长期记忆回答"<b>这个人是谁、偏好什么</b>"——用户画像，作用域 {@code subjectKey}，
 *         跨 threadId 稳定复用。</li>
 * </ul>
 * 两者都是"注入系统提示"，但来源、生命周期、检索方式完全不同，因此拆成两个拦截器：
 * 短期记忆是"取出来就用"，长期记忆要经过多路检索 + RRF 融合 + 冷热分层 + 预算裁剪，
 * 混在一个类里会让两条完全不同的链路互相纠缠。
 * <p>
 * 拦截器在模型调用前按顺序执行，两者都重写系统提示、且都读取"当前"的 systemMessage，
 * 因此可以安全串联：最终系统提示 = 长期记忆 + 对话摘要 + 原始系统提示词。
 * <p>
 * <b>失败降级</b>：任何异常都只记日志并原样调用下游。长期记忆是增强能力，
 * 它失效时用户应该"觉得不够个性化"，而不是"对话报错"。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class LongTermMemoryInterceptor extends ModelInterceptor {

    @Resource
    private MemoryContextBuilder memoryContextBuilder;

    @Override
    public String getName() {
        return "LongTermMemoryInterceptor";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        try {
            String subjectKey = resolveSubjectKey(request);
            if (!StringUtils.hasText(subjectKey)) {
                // 未登录且前端未提供 clientId：本轮不做长期记忆，短期记忆照常工作
                return handler.call(request);
            }
            String query = resolveLatestUserMessage(request);
            if (!StringUtils.hasText(query)) {
                return handler.call(request);
            }

            MemoryContextBuilder.MemoryPrompt memoryPrompt = memoryContextBuilder.build(subjectKey, query);
            if (memoryPrompt.isEmpty()) {
                return handler.call(request);
            }

            SystemMessage originalSystem = request.getSystemMessage();
            String originalText = originalSystem == null ? "" : originalSystem.getText();
            String enhancedSystem = memoryPrompt.text() + "\n" + originalText;

            ModelRequest enhancedRequest = ModelRequest.builder()
                    .systemMessage(new SystemMessage(enhancedSystem))
                    // 消息列表保持不动：长期记忆只影响系统提示，不改写对话轮次
                    .messages(request.getMessages())
                    .options(request.getOptions())
                    .tools(request.getTools())
                    .dynamicToolCallbacks(request.getDynamicToolCallbacks())
                    .toolDescriptions(request.getToolDescriptions())
                    .context(request.getContext())
                    .build();

            log.debug("注入长期记忆, subject: {}, 命中 {} 条, 文本长度 {}",
                    subjectKey, memoryPrompt.hits().size(), memoryPrompt.text().length());
            return handler.call(enhancedRequest);
        } catch (Exception e) {
            log.warn("长期记忆注入失败，本轮降级为无长期记忆: {}", e.getMessage());
            return handler.call(request);
        }
    }

    /**
     * 从 {@code RunnableConfig} 元数据取记忆主体键。
     * <p>
     * 与 conversationId 同理：整条链路跑在 Reactor 线程上，ThreadLocal 传不过来，
     * 元数据是唯一可靠通道。键名与召回工具共用 {@link MemoryConstants#SUBJECT_KEY_METADATA}，
     * 不在此处另立常量，避免两处同值常量各自漂移。
     */
    private String resolveSubjectKey(ModelRequest request) {
        Map<String, Object> context = request.getContext();
        if (context == null) {
            return null;
        }
        Object value = context.get(MemoryConstants.SUBJECT_KEY_METADATA);
        return value instanceof String subjectKey && !subjectKey.isBlank() ? subjectKey : null;
    }

    /**
     * 取本轮最新的用户发言作为检索输入。
     * <p>
     * 用最新一条而不是整轮拼接：多轮追问时（"那住宿呢"）整轮拼接会把历史话题的权重冲淡，
     * 检索结果反而更差。指代消解交给会话摘要（短期记忆）而不是检索输入。
     */
    private String resolveLatestUserMessage(ModelRequest request) {
        List<Message> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof UserMessage userMessage && StringUtils.hasText(userMessage.getText())) {
                return userMessage.getText();
            }
        }
        return null;
    }
}
