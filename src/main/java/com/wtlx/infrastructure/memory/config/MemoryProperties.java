package com.wtlx.infrastructure.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 长期记忆配置项（前缀 {@code trip-agent.memory}）。
 * <p>
 * 阈值集中在此处而非散落为常量，原因是这些数字都需要按真实对话分布标定
 * （与 RAG 的 {@code card-threshold} 同理）：换 embedding 模型、换抽取模型，
 * 相似度阈值必须重新标定，配在 YAML 里可以不改代码直接调。
 *
 * @author 金小梁
 */
@Data
@Component
@ConfigurationProperties(prefix = "trip-agent.memory")
public class MemoryProperties {

    /**
     * 长期记忆总开关；关闭后不抽取、不检索，只保留会话内短期记忆
     */
    private boolean enabled = true;

    // ==================== 抽取 ====================

    /**
     * 是否启用异步抽取；关闭后仅保留手工触发（管理接口）
     */
    private boolean extractionEnabled = true;

    /**
     * 触发抽取的最小用户消息长度：过短的寒暄（"你好""谢谢"）没有抽取价值，
     * 直接跳过以省一次 LLM 调用
     */
    private int extractionMinChars = 8;

    /**
     * 候选最低置信度，低于该值的候选直接丢弃（不进入合并流程）
     */
    private double extractionMinConfidence = 0.35D;

    /**
     * 单轮抽取候选数上限，防止模型在一次长对话里刷出大量低价值记忆
     */
    private int extractionMaxCandidates = 8;

    /**
     * 抽取任务线程池并发度
     */
    private int extractionPoolSize = 2;

    // ==================== 合并与冲突校验 ====================

    /**
     * 判定"语义重复"的相似度下限：达到即视为同一事实的不同表述，不新增记忆
     */
    private double duplicateSimilarity = 0.92D;

    /**
     * 判定"可能冲突"的相似度下限：低于该值直接新增；介于冲突线与重复线之间交给模型裁决
     */
    private double conflictSimilarity = 0.60D;

    /**
     * 是否启用冲突判定模型；关闭后只用规则（同槽位不同取值一律 SUPERSEDE），省 LLM 调用
     */
    private boolean conflictJudgeEnabled = true;

    /**
     * 冲突判定时拉取的相似记忆条数
     */
    private int semanticSearchTopK = 5;

    // ==================== 检索（两条链路） ====================

    /**
     * 融合后最终返回、并参与上下文注入的记忆条数
     */
    private int retrievalTopN = 8;

    /**
     * 语义路 · 向量召回条数
     */
    private int vectorTopK = 12;

    /**
     * 语义路 · 向量相似度阈值（cosine，0~1）
     */
    private double vectorThreshold = 0.45D;

    /**
     * 语义路 · 画像兜底条数：与查询无关，长期有效的高价值偏好，
     * 保证新会话第一句没提偏好时也能把画像带进上下文
     */
    private int stableFloorSize = 4;

    /**
     * 关键词路召回条数
     */
    private int keywordTopK = 8;

    /**
     * RRF 平滑常数 k，越大则各路排名差异被压得越平
     */
    private int rrfK = 60;

    /**
     * 各路权重（RRF 加权）：语义路是主路，关键词路只在 embedding 抖动、
     * 或中文专有名词被向量稀释时救场，故权重低
     */
    private double vectorWeight = 1.0D;

    private double keywordWeight = 0.60D;

    /**
     * 单条检索路的超时（毫秒）：任一路慢/挂都不拖垮对话，超时该路按空结果参与融合
     */
    private long pathTimeoutMs = 800L;

    /**
     * 检索结果是否异步回写命中统计（hit_count / last_hit_at）
     */
    private boolean trackHitStats = true;

    // ==================== 上下文注入 ====================

    /**
     * 注入模型的长期记忆文本上限（字符），超出按分数裁剪
     */
    private int contextMaxChars = 900;

    /**
     * 稳定层最多注入条数
     */
    private int stableMaxItems = 8;

    /**
     * 时效层最多注入条数
     */
    private int ephemeralMaxItems = 4;

    // ==================== 清理 ====================

    /**
     * 单主体 ACTIVE 记忆容量上限，超出按 置信度×重要度×新鲜度 淘汰
     */
    private int activeCapPerSubject = 200;

    /**
     * 低置信淘汰阈值
     */
    private double lowConfidenceThreshold = 0.30D;

    /**
     * 低置信记忆的"冷置期"：超过该天数且从未被命中过才淘汰，避免误杀新写入的记忆
     */
    private int lowConfidenceStaleDays = 30;

    /**
     * 非 ACTIVE 记忆的物理保留天数，到期彻底删除
     */
    private int purgeRetentionDays = 90;

    /**
     * 单次清理任务处理的主体数量上限，避免一次性扫全表
     */
    private int cleanupBatchSubjects = 200;
}
