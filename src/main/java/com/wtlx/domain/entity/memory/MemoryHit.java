package com.wtlx.domain.entity.memory;

import com.wtlx.domain.entity.memory.enums.MemoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 长期记忆检索命中结果。
 * <p>
 * 相比 {@link UserMemory}，它额外承载"多路检索"的中间信息：
 * {@code pathScores} 记录该条记忆在各条检索路上的原始得分，
 * 便于管理接口观察哪条路命中、融合后排在第几，是调参与排障的主要抓手。
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
     * 融合后的最终得分（RRF），仅用于排序，无绝对量纲
     */
    private double score;

    /**
     * 各检索路的原始得分：pathName → score。按路顺序插入，便于调试输出
     */
    @Builder.Default
    private Map<String, Double> pathScores = new LinkedHashMap<>();

    /**
     * 冷热分层：稳定层记忆（长期有效）参与上下文注入时可占用更多额度
     */
    public boolean isStable() {
        return validTo == null && type != null && type.isStable();
    }
}
