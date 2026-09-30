package com.wtlx.tools;

import com.wtlx.infrastructure.rag.adapter.RagRetrievalTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 集中的工具注册类
 * 合并本地工具和 MCP 工具
 * <p>
 * 本地工具均为 Spring Bean（通过 @Resource/@Value 完成注入），
 * 此处直接注入使用，不再手动 new。
 */
@Configuration
@Slf4j
public class ToolRegistration {

    /**
     * 注册所有工具（本地工具 + MCP 工具）
     *
     * @param mcpToolCallbackProvider MCP 工具回调提供者（由 Spring AI MCP 自动配置）
     * @param webSearchTool           网页搜索工具（Spring Bean）
     * @param ragRetrievalTool        RAG 知识库检索工具（Spring Bean）
     * @return 合并后的工具回调数组
     */
    @Bean
    public ToolCallback[] allTools(ToolCallbackProvider mcpToolCallbackProvider,
                                   WebSearchTool webSearchTool,
                                   RagRetrievalTool ragRetrievalTool) {
        log.info("开始注册工具集");

        // 1. 收集本地工具（均为 Spring Bean）
        // 注：PDF / Markdown 落盘生成工具已下线——输出形态改为"正文 + 思考步骤"结构化返回前端
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        TerminateTool terminateTool = new TerminateTool();

        ToolCallback[] localTools = ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                terminateTool,
                ragRetrievalTool
        );

        // 2. 获取 MCP 工具
        ToolCallback[] mcpTools = mcpToolCallbackProvider.getToolCallbacks();
        log.info("从 MCP 获取到 {} 个工具", mcpTools.length);

        // 3. 合并本地工具和 MCP 工具
        List<ToolCallback> allTools = new ArrayList<>();
        allTools.addAll(Arrays.asList(localTools));
        allTools.addAll(Arrays.asList(mcpTools));

        ToolCallback[] mergedTools = allTools.toArray(new ToolCallback[0]);
        log.info("工具集注册完成，本地工具 {} 个，MCP 工具 {} 个，共 {} 个工具",
                localTools.length, mcpTools.length, mergedTools.length);

        return mergedTools;
    }
}
