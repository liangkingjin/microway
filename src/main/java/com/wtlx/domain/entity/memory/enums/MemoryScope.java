package com.wtlx.domain.entity.memory.enums;

/**
 * 记忆作用域：区分"会话内短期记忆"与"跨会话长期记忆"。
 * <p>
 * 这是整套记忆体系的第一性划分，两者的存储、生命周期、检索方式完全不同：
 * <ul>
 *     <li>{@link #SESSION} —— 短期记忆，作用域为一次会话（threadId）。载体是
 *         {@code conversation_message} + {@code conversation_summary}（应用层）
 *         与 ReactAgent 的 {@code MemorySaver}（框架层）。回答"这一轮在聊什么"，
 *         随会话结束而失去价值，不跨会话复用。</li>
 *     <li>{@link #USER} —— 长期记忆，作用域为用户（subjectKey）。载体是 PG 的
 *         {@code user_memory} 表（含 pgvector 向量列）。回答"这个人是谁、偏好什么"，
 *         跨 threadId、跨会话稳定复用。</li>
 * </ul>
 *
 * @author 金小梁
 */
public enum MemoryScope {

    /**
     * 会话内短期记忆
     */
    SESSION("SESSION", "会话内短期记忆"),

    /**
     * 跨会话长期记忆
     */
    USER("USER", "跨会话长期记忆");

    private final String code;

    private final String label;

    MemoryScope(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
