package com.wtlx.infrastructure.memory.support;

/**
 * 记忆主体（长期记忆的作用域标识）。
 * <p>
 * 与短期记忆按 {@code threadId} 隔离不同，长期记忆必须锚定到"人"：
 * 同一个人的多个会话（threadId 各不相同）要共享同一份长期记忆，
 * 不同的人绝不能互相看到记忆。因此引入主体键作为唯一作用域边界。
 *
 * @param subjectKey    作用域键，形如 {@code user:123} / {@code guest:<clientId>}
 * @param userId        登录用户 id，游客为 null
 * @param authenticated 是否为登录用户
 * @author 金小梁
 */
public record MemoryPrincipal(String subjectKey, Long userId, boolean authenticated) {

    /**
     * 无主体（未登录且前端未提供 clientId）：本轮不读写长期记忆，只走会话内短期记忆
     */
    public static final MemoryPrincipal NONE = new MemoryPrincipal(null, null, false);

    public boolean available() {
        return subjectKey != null && !subjectKey.isBlank();
    }
}
