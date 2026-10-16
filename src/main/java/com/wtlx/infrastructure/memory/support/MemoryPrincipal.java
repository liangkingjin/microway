package com.wtlx.infrastructure.memory.support;

/**
 * 记忆主体（长期记忆的作用域标识）。
 * <p>
 * 与短期记忆按 {@code threadId} 隔离不同，长期记忆必须锚定到"人"：
 * 同一个人的多个会话（threadId 各不相同）要共享同一份长期记忆，
 * 不同的人绝不能互相看到记忆。因此引入主体键作为唯一作用域边界。
 * <p>
 * 刻意不再单独携带 {@code userId} 或登录标记：身份只有 {@code user:<userId>} 一种形态，
 * {@code MemorySubjectResolver} 已保证这一点——多带一个字段就多一处"两者不一致"的风险。
 * 落库时若需要 {@code user_id} 列，由 {@code MemoryMergeService#extractUserId} 从主体键解析——单一口径。
 *
 * @param subjectKey 作用域键，形如 {@code user:123}
 * @author 金小梁
 */
public record MemoryPrincipal(String subjectKey) {

    /**
     * 无主体（不应发生：{@code /ai/**} 已要求登录，此处仅作防御性兜底）
     */
    public static final MemoryPrincipal NONE = new MemoryPrincipal(null);

    public boolean available() {
        return subjectKey != null && !subjectKey.isBlank();
    }
}
