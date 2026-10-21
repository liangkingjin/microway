package com.wtlx.infrastructure.memory.support;

/**
 * 长期记忆体系常量。
 * <p>
 * 集中管理跨组件共享的字面量：RunnableConfig 元数据键、来源类型、缺省槽位。
 * 这些值一旦写错会导致"记忆串号"或"检索不到"，故不散落在各处。
 *
 * @author 金小梁
 */
public final class MemoryConstants {

    private MemoryConstants() {
    }

    /**
     * RunnableConfig 元数据中存放"当前登录用户 id"的位置。
     * <p>
     * 与 {@code ConversationMemoryInterceptor.CONVERSATION_ID_KEY} 同理：整条链路跑在
     * Reactor / ForkJoinPool 线程上，ThreadLocal 只是兜底，元数据才是可靠通道。
     * 长期记忆的作用域就是 userId 本身，不再包一层 {@code user:} 前缀。
     */
    public static final String USER_ID_METADATA = "memoryUserId";

    /**
     * 来源类型：由对话抽取。当前 {@code source_type} 只有这一种取值
     * （显式设置尚未开放入口，故不预留 {@code EXPLICIT} 常量）
     */
    public static final String SOURCE_CONVERSATION = "CONVERSATION";

    /**
     * 槽位键缺省值：模型未给出槽位时使用，保证同一类型的信息仍能聚到同一槽位做冲突校验
     */
    public static final String DEFAULT_MEMORY_KEY = "general";
}
