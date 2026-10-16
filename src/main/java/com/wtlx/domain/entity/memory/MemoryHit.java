package com.wtlx.domain.entity.memory;

import com.wtlx.domain.entity.memory.enums.MemoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 长期记忆检索命中结果。
 * <p>
 * 相比 {@link UserMemory}，它是面向"上下文注入与接口返回"的精简视图：
 * 去掉了行主键、去重键等内部字段，保留渲染所需的类型 / 内容 / 置信度 / 有效期，
 * {@code score} 为检索相似度（余弦，0~1），用于排序与调试输出。
 *
 * @author 金小梁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryHit implements Serializable {

    private static final long serialVersionUID = 1L;

    private String memoryId;

    private MemoryType type;

    private String key;

    private String content;

    private String structured;

    private double confidence;

    private double importance;

    /**
     * 有效期止；null 表示长期有效（稳定层）
     */
    private LocalDateTime validTo;

    private LocalDateTime updatedAt;

    /**
     * 检索相似度（余弦，0~1），仅用于排序与调试输出
     */
    private double score;

    /**
     * 冷热分层：稳定层记忆（长期有效）参与上下文注入时可占用更多额度
     */
    public boolean isStable() {
        return validTo == null && type != null && type.isStable();
    }
}
