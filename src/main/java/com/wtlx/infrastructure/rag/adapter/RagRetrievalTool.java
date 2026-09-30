package com.wtlx.infrastructure.rag.adapter;

import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.infrastructure.rag.retriever.RagHit;
import com.wtlx.infrastructure.rag.config.RagProperties;
import com.wtlx.infrastructure.rag.retriever.RagSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索工具
 * <p>
 * 将 RAG（Retrieval Augmented Generation，检索增强生成）的检索阶段封装为 Agent 可调用的工具：
 * Agent 需要旅游领域参考时主动调用，从<b>站内已发布的旅游游记与官方攻略</b>中做相似度检索，
 * 拿回带来源标识的片段，再基于检索结果作答。
 * <p>
 * 检索参数取自 {@code trip-agent.rag.search-top-k} 与 {@code search-threshold}。
 */
@Slf4j
@Component
public class RagRetrievalTool {

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagProperties ragProperties;

    /**
     * 从站内旅游游记与官方攻略中检索与问题相关的内容
     *
     * @param query 用于检索的查询内容，通常是用户的旅游相关问题
     * @return 检索到的内容片段（带来源标题与类型）；未检索到时返回明确提示
     */
    @Tool(description = "从站内已发布的旅游游记与官方攻略中检索与用户问题相关的内容，覆盖目的地玩法、行程安排、食宿交通等真实经验；需要旅游参考知识时调用本工具")
    public String retrieveKnowledge(
            @ToolParam(description = "用于检索的查询内容，通常是用户的旅游相关问题") String query) {
        log.info("RAG 站内检索，query: {}", query);

        List<RagHit> hits = ragSearchService.search(query, ragProperties.getSearchTopK(),
                ragProperties.getSearchThreshold(), List.of(RagDocType.BLOG, RagDocType.GUIDE));

        if (hits.isEmpty()) {
            log.info("RAG 未检索到相关内容，query: {}", query);
            return "站内未检索到与该问题相关的游记或攻略: " + query;
        }

        String context = hits.stream()
                .map(this::format)
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("RAG 检索到 {} 条相关内容", hits.size());
        return context;
    }

    /**
     * 格式化单条命中：带上来源标题与类型，便于模型引用与区分
     */
    private String format(RagHit hit) {
        Object title = hit.getMetadata() == null ? null : hit.getMetadata().get("title");
        Object docType = hit.getMetadata() == null ? null : hit.getMetadata().get("docType");
        String source = RagDocType.GUIDE.equals(String.valueOf(docType)) ? "官方攻略" : "站内游记";
        return "【" + source + "】" + (title == null ? "" : title) + "\n" + hit.getText();
    }
}
