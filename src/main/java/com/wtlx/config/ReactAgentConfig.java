package com.wtlx.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.wtlx.infrastructure.advisor.ConversationMemoryInterceptor;
import com.wtlx.infrastructure.advisor.LongTermMemoryInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ReactAgent 配置类
 * 基于 SpringAI Alibaba Agent Framework 的 ReactAgent 配置
 */
@Slf4j
@Configuration
public class ReactAgentConfig {

    @Value("classpath:/prompts/trip-planner-system.st")
    private Resource systemPromptResource;

    /**
     * 创建 ReactAgent Bean
     *
     * @param chatModel         对话模型（OpenAI 协议接入的阿里云百炼兼容模式端点）
     * @param allTools          所有工具（本地 + MCP）
     * @param memoryInterceptor 会话内短期记忆拦截器（摘要 + 最近若干轮）
     * @param longTermInterceptor 跨会话长期记忆拦截器（用户画像，多路检索 + RRF 融合后注入）
     * @return ReactAgent 实例
     */
    @Bean
    public ReactAgent tripReactAgent(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            ToolCallback[] allTools,
            ConversationMemoryInterceptor memoryInterceptor,
            LongTermMemoryInterceptor longTermInterceptor) {

        // 加载系统提示词
        String instruction = loadSystemPrompt();

        log.info("Initializing ReactAgent with {} tools", allTools.length);

        return ReactAgent.builder()
                .name("TripPlannerReactAgent")
                .model(chatModel)
                .tools(allTools)
                .instruction(instruction)
                .saver(new MemorySaver())
                // 拦截器按顺序串联，两者都重写系统提示且都读取"当前"的 systemMessage，
                // 因此最终系统提示 = 长期记忆 + 对话摘要 + 原始提示词；短期记忆在前是为了
                // 让"最近在聊什么"离原始系统提示更近，长期记忆作为背景信息在最外层
                .interceptors(List.of(memoryInterceptor, longTermInterceptor))
                .hooks(ModelCallLimitHook.builder().runLimit(20).build())
                .build();
    }

    /**
     * 从资源文件加载系统提示词
     */
    private String loadSystemPrompt() {
        try {
            return systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load system prompt", e);
            throw new RuntimeException("无法加载系统提示词", e);
        }
    }
}
