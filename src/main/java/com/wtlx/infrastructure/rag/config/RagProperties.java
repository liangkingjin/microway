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
     * 给模型的检索上下文条数（宽召回结果的前 N 条）
     */
    private int searchTopK = 5;

    /**
     * 检索相似度阈值（0~1，越大越严）；宽召回与给模型的上下文共用
     */
    private double searchThreshold = 0.5;

    /**
     * 宽召回条数：一次检索同时覆盖"给模型的上下文"与"卡片候选"，
     * 必须不小于 search-top-k 与 card-top-k，否则卡片候选会被给模型的条数截断
     */
    private int candidateTopK = 8;

    /**
     * 前端卡片取用条数
     */
    private int cardTopK = 4;

    /**
     * 卡片相似度阈值；卡片直接暴露给用户，比 search-threshold 更严，低于该值不产出卡片
     */
    private double cardThreshold = 0.55;

    /**
     * 启动时是否对缺失索引的来源做全量回填
     */
    private boolean rebuildOnStartup = false;
}
