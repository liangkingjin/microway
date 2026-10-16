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
     * 段级触发阈值：每累计这么多轮对话，才触发一次"该段会话记录"的提取。
     * 写入链路的核心简化点——从"每轮都抽"改为"每 N 轮抽一段"
     */
    private int extractionEveryRounds = 10;

    /**
     * 单次抽取覆盖的段大小（单位：轮）。取最近这么多轮作为待抽取记录，
     * 故实际加载的消息条数为 {@code segmentMaxRounds * 2}
     */
    private int segmentMaxRounds = 10;

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

    // ==================== 检索（单路向量） ====================

    /**
     * 融合后最终返回、并参与上下文注入的记忆条数
     */
    private int retrievalTopN = 8;

    /**
     * 向量召回条数
     */
    private int vectorTopK = 12;

    /**
     * 向量相似度阈值（cosine，0~1）
     */
    private double vectorThreshold = 0.45D;

    // ==================== 上下文注入 ====================

    /**
     * 注入模型的长期记忆文本上限（字符），超出按分数裁剪
     */
    private int contextMaxChars = 900;

    /**
     * 稳定层（长期有效的高价值偏好）最多注入条数
     */
    private int stableMaxItems = 8;

    /**
     * 时效层（带有效期的行程 / 预算）最多注入条数
     */
    private int ephemeralMaxItems = 4;
}
