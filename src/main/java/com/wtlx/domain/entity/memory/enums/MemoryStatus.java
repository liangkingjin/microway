package com.wtlx.domain.entity.memory.enums;

/**
 * 长期记忆生命周期状态。
 * <p>
 * 删除一律是<b>软删</b>：记忆的冲突判定依赖历史证据，直接物理删除会让"用户改口"这类
 * 关键过程不可追溯，因此先用状态表达语义，物理清除交给清理任务按保留期执行。
 *
 * @author 金小梁
 */
public enum MemoryStatus {

    /**
     * 生效中，参与检索与上下文注入
     */
    ACTIVE("ACTIVE", "生效中"),

    /**
     * 已被更新的记忆取代（同槽位新值覆盖旧值），保留用于审计
     */
    SUPERSEDED("SUPERSEDED", "已被取代"),

    /**
     * 已过期：有效期到点，或被用户明确否定
     */
    EXPIRED("EXPIRED", "已过期"),

    /**
     * 用户主动删除（按需清理）
     */
    DELETED("DELETED", "已删除");

    private final String code;

    private final String label;

    MemoryStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static MemoryStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ACTIVE;
        }
        String normalized = code.trim().toUpperCase();
        for (MemoryStatus status : values()) {
            if (status.code.equals(normalized)) {
                return status;
            }
        }
        return ACTIVE;
    }
}
