package com.wtlx.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.wtlx.ai.advisor.ConversationMemoryInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

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
     * @param dashscopeChatModel DashScope 聊天模型
     * @param allTools           所有工具（本地 + MCP）
     * @param memoryInterceptor  对话记忆拦截器（Spring Bean，内部通过 @Resource 注入记忆服务）
     * @return ReactAgent 实例
     */
    @Bean
    public ReactAgent tripReactAgent(
            ChatModel dashscopeChatModel,
            ToolCallback[] allTools,
            ConversationMemoryInterceptor memoryInterceptor) {

        // 加载系统提示词
        String instruction = loadSystemPrompt();

        log.info("Initializing ReactAgent with {} tools", allTools.length);

        return ReactAgent.builder()
                .name("TripPlannerReactAgent")
                .model(dashscopeChatModel)
                .tools(allTools)
                .instruction(instruction)
                .saver(new MemorySaver())
                .interceptors(Collections.singletonList(memoryInterceptor))
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
