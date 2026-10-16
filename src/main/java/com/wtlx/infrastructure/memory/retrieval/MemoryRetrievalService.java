package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.MemoryHit;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.domain.entity.memory.enums.MemoryType;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.embedding.MemoryEmbeddingService;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆向量检索服务（单路）。
 * <p>
 * 只保留语义向量一路：查询向量在此<b>统一计算一次</b>后交给
 * {@code userMemoryMapper.searchByVector}，按余弦相似度召回，再直接构造成
 * {@link MemoryHit} 返回（按相似度降序、截断 {@code retrievalTopN}）。
 * <p>
 * <b>为什么砍掉双路</b>：原先"语义路 + 关键词路"并行 + RRF 融合 + 画像兜底，
 * 实测维护成本高、收益有限——关键词路在高基 embedding 下大多召不回额外结果，
 * 画像兜底与向量路高度重叠。单路更简单、可预期，且仍满足"换个说法也能命中"的核心诉求。
 * <p>
 * <b>记忆是增强不是依赖</b>：开关关闭、主体/查询缺失或 embedding 不可用时，本服务直接返回空列表，
 * 对话照常进行，绝不被记忆链路拖垮。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class MemoryRetrievalService {

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private MemoryEmbeddingService memoryEmbeddingService;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    /**
     * 按默认配置检索（retrievalTopN 条）
     */
    public List<MemoryHit> retrieve(String subjectKey, String query) {
        return retrieve(subjectKey, query, memoryProperties.getRetrievalTopN());
    }

    /**
     * 单路向量检索
     *
     * @param subjectKey 记忆主体键
     * @param query      用户查询
     * @param topN       返回条数
     * @return 命中，按相似度降序；主体缺失、开关关闭、无查询或 embedding 不可用时返回空列表
     */
    public List<MemoryHit> retrieve(String subjectKey, String query, int topN) {
        if (!memoryProperties.isEnabled() || !StringUtils.hasText(subjectKey) || !StringUtils.hasText(query)) {
            return List.of();
        }

        // 查询向量统一计算一次（只算一次，避免重复计费）
        String queryVectorLiteral = memoryEmbeddingService.embedToLiteral(query);
        if (!StringUtils.hasText(queryVectorLiteral)) {
            // embedding 不可用：记忆是增强不是依赖，直接退化为无长期记忆
            log.debug("查询向量计算失败，长期记忆检索退化为空结果");
            return List.of();
        }

        List<ScoredMemory> scored = userMemoryMapper.searchByVector(
                subjectKey, queryVectorLiteral, memoryProperties.getVectorTopK(), memoryProperties.getVectorThreshold());
        if (scored == null || scored.isEmpty()) {
            log.info("长期记忆检索完成, subject: {}, 命中 0 条", subjectKey);
            return List.of();
        }

        // SQL 已按相似度降序返回；按 topN 截断后直接构造成 MemoryHit
        List<MemoryHit> hits = new ArrayList<>(Math.min(scored.size(), topN));
        for (ScoredMemory scoredMemory : scored) {
            if (hits.size() >= topN) {
                break;
            }
            MemoryHit hit = toHit(scoredMemory);
            if (hit != null) {
                hits.add(hit);
            }
        }
        log.info("长期记忆检索完成, subject: {}, 命中 {} 条", subjectKey, hits.size());
        return hits;
    }

    /**
     * ScoredMemory → MemoryHit：把向量相似度作为排序分，并补齐渲染所需字段
     */
    private MemoryHit toHit(ScoredMemory scoredMemory) {
        UserMemory memory = scoredMemory.memory();
        if (memory == null) {
            return null;
        }
        return MemoryHit.builder()
                .memoryId(memory.getMemoryId())
                .type(MemoryType.fromCode(memory.getMemoryType()))
                .key(memory.getMemoryKey())
                .content(memory.getContent())
                .structured(memory.getStructured())
                .confidence(memory.getConfidence())
                .importance(memory.getImportance())
                .validTo(memory.getValidTo())
                .updatedAt(memory.getUpdatedAt())
                .score(scoredMemory.pathScore())
                .build();
    }
}
