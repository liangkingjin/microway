package com.wtlx.infrastructure.rag.retriever;

import com.wtlx.infrastructure.rag.config.RagProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 相似度检索实现。
 * <p>
 * 检索源为两类站内资源（游记 / 官方攻略），通过 metadata 上的 {@code docType} 过滤，
 * 避免与历史遗留文档切片互相干扰。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class RagSearchServiceImpl implements RagSearchService {

    /**
     * 余弦距离 → 相似度的转换基准（PGVector 返回的是距离，写在 metadata 的 distance 上）
     */
    private static final String DISTANCE_KEY = "distance";

    @Resource
    private VectorStore vectorStore;

    @Resource
    private RagProperties ragProperties;

    @Override
    public List<RagHit> search(String query, int topK, double threshold, List<String> docTypes) {
        if (!ragProperties.isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK <= 0 ? ragProperties.getSearchTopK() : topK)
                    .similarityThreshold(threshold);
            Filter.Expression expression = buildFilter(docTypes);
            if (expression != null) {
                builder.filterExpression(expression);
            }
            List<Document> documents = vectorStore.similaritySearch(builder.build());
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }
            List<RagHit> hits = new ArrayList<>(documents.size());
            for (Document document : documents) {
                hits.add(RagHit.builder()
                        .text(document.getText())
                        .score(resolveScore(document))
                        .metadata(document.getMetadata() == null ? java.util.Map.of() : document.getMetadata())
                        .build());
            }
            hits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return hits;
        } catch (Exception e) {
            log.error("RAG 检索失败, query: {}", query, e);
            return List.of();
        }
    }

    /**
     * 构造来源类型过滤表达式
     */
    private Filter.Expression buildFilter(List<String> docTypes) {
        if (docTypes == null || docTypes.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        if (docTypes.size() == 1) {
            return feb.eq("docType", docTypes.get(0)).build();
        }
        return feb.in("docType", docTypes.toArray()).build();
    }

    /**
     * 取相似度：优先用文档自带 score，否则由余弦距离换算
     */
    private double resolveScore(Document document) {
        if (document.getScore() != null) {
            return document.getScore();
        }
        Object distance = document.getMetadata() == null ? null : document.getMetadata().get(DISTANCE_KEY);
        if (distance instanceof Number number) {
            return 1.0D - number.doubleValue();
        }
        return 0.0D;
    }
}
