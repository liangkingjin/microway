package com.wtlx.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;

/**
 * RAG 知识库检索工具
 * <p>
 * 将 RAG（Retrieval Augmented Generation，检索增强生成）的检索阶段封装为 Agent 可调用的工具：
 * Agent 在需要旅游领域知识时，主动调用本工具，从旅游知识库向量库中做相似度检索，
 * 获取出行准备、国内游、国外游等常见问题与攻略片段，再基于检索结果生成回答。
 * <p>
 * 检索参数：topK=3（返回最相关的 3 个片段），similarityThreshold=0.5（相似度阈值）
 */
@Slf4j
@Component
public class RagRetrievalTool {

    /**
     * 每次检索返回的最相关文档片段数量
     */
    private static final int TOP_K = 3;

    /**
     * 相似度阈值，只返回相似度 >= 0.5 的文档
     */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    @Resource
    private VectorStore vectorStore;

    /**
     * 从旅游知识库中检索与问题相关的知识片段
     *
     * @param query 用于检索知识库的查询内容，通常是用户的旅游相关问题
     * @return 检索到的知识片段拼接文本；未检索到时返回提示信息
     */
    @Tool(description = "从旅游知识库中检索与用户问题相关的旅游知识，覆盖出行准备、国内游、国外游的常见问题与攻略；当需要旅游领域的参考知识来回答用户时调用本工具")
    public String retrieveKnowledge(
            @ToolParam(description = "用于检索知识库的查询内容，通常是用户的旅游相关问题") String query) {
        log.info("RAG 知识库检索，query: {}", query);

        try {
            // 构建向量相似度检索请求
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            // 未检索到相关文档
            if (documents == null || documents.isEmpty()) {
                log.warn("RAG 未检索到相关文档，query: {}", query);
                return "知识库中未检索到与该问题相关的内容: " + query;
            }

            // 将检索到的文档片段拼接为上下文
            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("RAG 检索到 {} 条相关文档", documents.size());
            return context;
        } catch (Exception e) {
            log.error("RAG 检索失败: {}", e.getMessage(), e);
            return "知识库检索失败: " + e.getMessage();
        }
    }
}
