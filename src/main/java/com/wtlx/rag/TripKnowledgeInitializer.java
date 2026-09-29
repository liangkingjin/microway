package com.wtlx.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 旅游知识库初始化器
 * <p>
 * 向量存储（{@link VectorStore}）由 PGVector starter 自动配置，
 * 底层为 PostgreSQL 的 pgvector 扩展；本类不再自行创建向量库，
 * 只负责在应用启动后把旅游知识库文档加载并写入向量库。
 * <p>
 * 通过 trip-agent.rag.enabled=true/false 控制是否初始化知识库。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "trip-agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class TripKnowledgeInitializer {

    /**
     * PGVector 默认向量表名，与 application.yml 中配置保持一致
     */
    private static final String VECTOR_TABLE = "vector_store";

    /**
     * 单次 embedding 批量大小，DashScope text-embedding 接口要求单批不超过 10 条
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    @Bean
    public ApplicationRunner initTripKnowledge(VectorStore vectorStore,
                                               TripDocumentLoader tripDocumentLoader,
                                               JdbcTemplate jdbcTemplate) {
        return args -> {
            // 向量表已有数据则跳过，避免每次重启重复写入
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + VECTOR_TABLE, Integer.class);
            if (count != null && count > 0) {
                log.info("向量库已存在 {} 条向量数据，跳过知识库初始化", count);
                return;
            }

            // 加载知识库 Markdown 文档并写入向量库
            List<Document> documents = tripDocumentLoader.loadMarkdowns();
            if (documents == null || documents.isEmpty()) {
                log.warn("未加载到任何知识库文档，跳过知识库初始化");
                return;
            }

            // 分批写入，避免超出 DashScope 单批 embedding 上限（10 条）
            int total = 0;
            for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
                List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
                vectorStore.add(batch);
                total += batch.size();
            }
            log.info("旅游知识库初始化完成，共写入 {} 个文档片段", total);
        };
    }
}
