package com.wtlx.infrastructure.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索配置项（前缀 {@code trip-agent.rag}）
 *
 * @author 金小梁
 */
@Data
@Component
@ConfigurationProperties(prefix = "trip-agent.rag")
public class RagProperties {

    /**
     * 是否启用 RAG 能力；关闭后检索工具直接返回空，且不做任何向量写入
     */
    private boolean enabled = true;

    /**
     * 单个切片目标字符数
     */
    private int chunkSize = 500;

    /**
     * 相邻切片重叠字符数，避免语义在切点断裂
     */
    private int chunkOverlap = 80;

    /**
     * 单个来源最大切片数，超出截断，防止超长文本打爆 embedding 配额
     */
    private int maxChunks = 50;

    /**
     * 单批 embedding 条数（DashScope text-embedding 接口上限 10）
     */
    private int embeddingBatchSize = 10;

    /**
     * Agent 检索工具默认返回条数
     */
    private int searchTopK = 5;

    /**
     * Agent 检索工具相似度阈值（0~1，越大越严）
     */
    private double searchThreshold = 0.35;

    /**
     * 前端卡片取用条数
     */
    private int cardTopK = 4;

    /**
     * 卡片相似度阈值；低于该值认为"未检索到相关游记"，不渲染卡片区
     */
    private double cardThreshold = 0.3;

    /**
     * 启动时是否对缺失索引的来源做全量回填
     */
    private boolean rebuildOnStartup = false;
}
