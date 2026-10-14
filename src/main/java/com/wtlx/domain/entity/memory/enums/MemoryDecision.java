package com.wtlx.domain.entity.memory.enums;

/**
 * 记忆合并决策。
 * <p>
 * 抽取出的候选信息并不直接落库，必须先与已有的同主体记忆做一次"合并 / 冲突校验"，
 * 得到以下五种决策之一后才写库。决策空间刻意保持正交且穷尽：
 * <ul>
 *     <li>{@link #ADD} —— 无冲突，新增一条 ACTIVE 记忆</li>
 *     <li>{@link #UPDATE} —— 同槽位、同语义的补充信息，就地丰富已有记忆（不产生新行）</li>
 *     <li>{@link #SUPERSEDE} —— 同槽位、语义矛盾（用户改口），旧记忆置 SUPERSEDED 并指向新记忆</li>
 *     <li>{@link #FORGET} —— 用户明确否定且新信息本身无需留存，旧记忆直接置 EXPIRED</li>
 *     <li>{@link #NOOP} —— 重复信息，只做置信度巩固与证据追加</li>
 * </ul>
 * 除 {@link #CLEANUP} 外，每个决策都会在 {@code user_memory_event} 留下审计记录。
 *
 * @author 金小梁
 */
public enum MemoryDecision {

    ADD("ADD"),

    UPDATE("UPDATE"),

    SUPERSEDE("SUPERSEDE"),

    FORGET("FORGET"),

    NOOP("NOOP"),

    /**
     * 由清理任务触发的批量失效（过期 / 低置信 / 超容量 / 冗余），非抽取流程产生
     */
    CLEANUP("CLEANUP");

    /**
     * 落库编码，与 {@code user_memory_event.decision} 列取值一致
     */
    private final String code;

    MemoryDecision(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MemoryDecision fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NOOP;
        }
        String normalized = code.trim().toUpperCase();
        for (MemoryDecision decision : values()) {
            if (decision.code.equals(normalized)) {
                return decision;
            }
        }
        return NOOP;
    }
}
