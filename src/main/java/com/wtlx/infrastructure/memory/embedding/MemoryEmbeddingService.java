package com.wtlx.infrastructure.memory.embedding;

import com.wtlx.infrastructure.memory.support.VectorLiterals;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 记忆向量化服务。
 * <p>
 * 与 RAG 共用同一个 embedding 模型实例（DashScope {@code text-embedding-v3}，1024 维），
 * 因此 <b>user_memory.embedding 与 vector_store.embedding 必须同为 1024 维</b>；
 * 换 embedding 模型时两处列定义与 {@code spring.ai.vectorstore.pgvector.dimensions} 需同步修改，
 * 且相似度阈值（{@code trip-agent.memory.vector-threshold} 等）必须重新标定。
 * <p>
 * 失败策略：向量化失败不阻断记忆写入，返回 null 让 {@code embedding} 列落空值——
 * 该条记忆仍可被关键词路召回，也仍能被语义路的画像兜底带上，只是暂时进不了向量召回那一半。
 * 记记忆比记向量重要。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryEmbeddingService {

    @Resource
    @Qualifier("dashscopeEmbeddingModel")
    private EmbeddingModel embeddingModel;

    /**
     * 文本向量化
     *
     * @param text 文本
     * @return 向量；失败或文本为空时返回 null
     */
    private float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("记忆向量化失败，该条记忆将不参与向量检索: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 文本向量化并直接转成 pgvector 字面量，供 SQL 的 {@code CAST(? AS vector)} 使用
     *
     * @return 字面量；失败时返回 null
     */
    public String embedToLiteral(String text) {
        return VectorLiterals.toLiteral(embed(text));
    }
}
