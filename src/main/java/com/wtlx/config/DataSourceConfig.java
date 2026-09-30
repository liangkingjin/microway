package com.wtlx.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置（AI 收口后引入）
 * <p>
 * - 主数据源（MySQL）：业务服务，MyBatis-Plus 自动绑定
 * - AI 数据源（PostgreSQL）：对话记忆（JdbcTemplate）+ PGVector 向量库
 * <p>
 * 实现说明：数据源统一通过 {@link DataSourceProperties} 绑定配置（url/username/password 等
 * 标准字段），再用 initializeDataSourceBuilder() 构建 Hikari 数据源——避免 @ConfigurationProperties
 * 直接绑到 HikariDataSource 时 url 无法映射为 jdbcUrl 的问题。
 * <p>
 * PGVectorStore 必须显式绑定 AI 数据源，否则 Spring AI 自动配置会把向量表建到主数据源（MySQL）上。
 */
@Configuration
public class DataSourceConfig {

    /**
     * 业务主数据源配置（前缀 spring.datasource）
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 业务主数据源：MySQL（MyBatis-Plus 自动绑定，@Primary）
     */
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("dataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * AI 数据源配置（自定义前缀 ai.datasource，避免与 spring.datasource 绑定交叉）
     */
    @Bean
    @ConfigurationProperties("ai.datasource")
    public DataSourceProperties aiDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * AI 数据源：PostgreSQL
     */
    @Bean("aiDataSource")
    public DataSource aiDataSource(@Qualifier("aiDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * AI 侧 JdbcTemplate（对话记忆 mapper 使用）
     */
    @Bean("aiJdbcTemplate")
    public JdbcTemplate aiJdbcTemplate(@Qualifier("aiDataSource") DataSource aiDataSource) {
        return new JdbcTemplate(aiDataSource);
    }

    /**
     * PGVector 向量库（显式使用 AI 数据源；同时让 Spring AI 的自动配置因
     * {@code @ConditionalOnMissingBean(PgVectorStore.class)} 跳过 PgVectorStore 的创建，
     * 但 PgVectorStoreProperties 由自动配置提供（绑定 spring.ai.vectorstore.pgvector）
     * <p>
     * schema 初始化由 {@link PgVectorStore#afterPropertiesSet()} 完成（InitializingBean），
     * builder 的 initializeSchema(true) 会在其中建 pgvector 扩展与向量表。
     */
    @Bean
    public PgVectorStore pgVectorStore(@Qualifier("aiJdbcTemplate") JdbcTemplate aiJdbcTemplate,
                                       @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
                                       PgVectorStoreProperties properties) {
        return PgVectorStore.builder(aiJdbcTemplate, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .dimensions(properties.getDimensions())
                .indexType(properties.getIndexType() != null
                        ? properties.getIndexType()
                        : PgVectorStore.PgIndexType.HNSW)
                .distanceType(properties.getDistanceType() != null
                        ? properties.getDistanceType()
                        : PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .schemaName(properties.getSchemaName())
                .vectorTableName(properties.getTableName())
                .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
                .idType(properties.getIdType())
                .build();
    }
}
