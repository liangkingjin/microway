package com.wtlx.domain.entity.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户长期记忆实体（跨会话），对应 PostgreSQL 表 {@code user_memory}。
 * <p>
 * 一条记忆 = 主体的一个"事实断言"：{@code content} 是自然语言表述，
 * {@code memoryKey} 是归一化槽位（同一槽位的不同值即潜在冲突），
 * {@code structured} 是可程序化读取的结构化槽位值。
 * <p>
 * 向量列 {@code embedding}（1024 维）不在此实体中映射——检索侧不需要回读向量，
 * 只在写入与相似查询时由 SQL 侧构造，避免无谓地把 1024 个 float 拉回 JVM。
 *
 * @author 金小梁
 */
@Data
public class UserMemory {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 对外稳定标识（UUID）。取代关系、事件审计、前端按条删除都用它，避免暴露自增 id
     */
    private String memoryId;

    /**
     * 记忆主体键，恒为登录用户的 {@code user:<userId>}。
     * 这是长期记忆的作用域边界，所有读写必须带它，防止跨用户串记忆
     */
    private String subjectKey;

    /**
     * 登录用户 id；游客记忆为 null
     */
    private String userId;

    /**
     * 记忆类型编码，见 {@code MemoryType}
     */
    private String memoryType;

    /**
     * 归一化槽位键，如 {@code budget.level} / {@code dietary.restriction} / {@code destination.prefer}
     */
    private String memoryKey;

    /**
     * 记忆的自然语言表述（同时是向量化的文本）
     */
    private String content;

    /**
     * 结构化槽位值（jsonb 文本），如 {@code {"level":"经济","daily":300}}
     */
    private String structured;

    /**
     * 置信度 0~1，由抽取模型给出，重复命中会巩固提升
     */
    private double confidence;

    /**
     * 重要度 0~1，表示该信息对个性化决策的权重，参与容量淘汰排序
     */
    private double importance;

    /**
     * 生命周期状态编码，见 {@code MemoryStatus}
     */
    private String status;

    /**
     * 来源类型：{@code CONVERSATION} / {@code EXPLICIT} / {@code PROFILE}
     */
    private String sourceType;

    /**
     * 来源引用：会话场景下为 {@code conversationId}
     */
    private String sourceRef;

    /**
     * 抽取依据原文（用户原句），用于冲突判定与人工复核时回溯
     */
    private String evidence;

    /**
     * 有效期起
     */
    private LocalDateTime validFrom;

    /**
     * 有效期止；为 null 表示长期有效（稳定层记忆）
     */
    private LocalDateTime validTo;

    /**
     * 被哪条记忆取代（{@code memoryId}），仅 status = SUPERSEDED 时有值
     */
    private String supersededBy;

    /**
     * 版本号，每次就地更新 +1
     */
    private int version;

    /**
     * 被检索命中次数，体现复用强度，也是清理时判断"是否还有人用得上"的依据
     */
    private int hitCount;

    /**
     * 最近一次被检索命中的时间
     */
    private LocalDateTime lastHitAt;

    /**
     * 内容指纹（sha256），用于抽取级幂等与精确查重
     */
    private String contentHash;

    /**
     * 去重键：{@code type|key|valueHash}。配合 ACTIVE 部分唯一索引，
     * 保证同一主体同一槽位同一取值在库内至多一条生效记忆
     */
    private String dedupKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 写入用的向量字面量（pgvector 文本形式），<b>只写不读</b>。
     * <p>
     * 不映射 {@code embedding} 列是有意的：检索侧从不回读向量，回读一次等于白拉 4KB
     * 并解析 1024 个浮点。故这里保留一个仅用于 INSERT 的透传字段，
     * 由 {@code MemoryEmbeddingService} 在写入前填充。
     */
    private String embeddingLiteral;
}
