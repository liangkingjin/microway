package com.wtlx.domain.entity.memory;

import com.wtlx.domain.entity.memory.enums.MemoryType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆抽取候选（子 Agent 的结构化输出）。
 * <p>
 * 这是"对话 → 长期记忆"之间的中间态：抽取模型只负责把自然语言拆成候选，
 * <b>不具备写库权限</b>；候选必须经合并 / 冲突校验后才能变成 {@link UserMemory}。
 * 这样把"理解"和"裁决"分成两步，模型单次幻觉不会直接污染长期记忆。
 *
 * @author 金小梁
 */
@Data
public class MemoryCandidate {

    /**
     * 记忆类型（模型输出编码，解析时兜底为 FACT）
     */
    private MemoryType type = MemoryType.FACT;

    /**
     * 归一化槽位键，如 {@code budget.level}
     */
    private String key;

    /**
     * 自然语言表述
     */
    private String content;

    /**
     * 结构化槽位值（JSON 对象字符串），可为 null
     */
    private String structured;

    /**
     * 置信度 0~1
     */
    private double confidence = 0.6D;

    /**
     * 重要度 0~1
     */
    private double importance = 0.5D;

    /**
     * 有效期止；相对时间（"下个月""明年暑假"）应由模型归一化为绝对日期，null 表示长期有效
     */
    private LocalDateTime validTo;

    /**
     * 抽取依据原文
     */
    private String evidence;
}
