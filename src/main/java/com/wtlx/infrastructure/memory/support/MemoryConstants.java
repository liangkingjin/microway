package com.wtlx.infrastructure.memory.support;

/**
 * 长期记忆体系常量。
 * <p>
 * 集中管理跨组件共享的字面量：RunnableConfig 元数据键、主体前缀、缓存键。
 * 这些值一旦写错会导致"记忆串号"或"检索不到"，故不散落在各处。
 *
 * @author 金小梁
 */
public final class MemoryConstants {

    private MemoryConstants() {
    }

    /**
     * RunnableConfig 元数据中存放记忆主体键的位置。
     * <p>
     * 与 {@code ConversationMemoryInterceptor.CONVERSATION_ID_KEY} 同理：整条链路跑在
     * Reactor / ForkJoinPool 线程上，ThreadLocal 只是兜底，元数据才是可靠通道。
     */
    public static final String SUBJECT_KEY_METADATA = "memorySubjectKey";

    /**
     * 登录用户主体前缀
     */
    public static final String SUBJECT_USER_PREFIX = "user:";

    /**
     * 游客主体前缀（无登录态时用前端持久化的客户端 id 兜底）
     */
    public static final String SUBJECT_GUEST_PREFIX = "guest:";

    /**
     * 来源类型：由对话抽取
     */
    public static final String SOURCE_CONVERSATION = "CONVERSATION";

    /**
     * 来源类型：用户显式设置
     */
    public static final String SOURCE_EXPLICIT = "EXPLICIT";

    /**
     * 稳定层记忆缓存键前缀，完整键为 {@code ai:memory:stable:<subjectKey>}
     */
    public static final String CACHE_STABLE_PREFIX = "ai:memory:stable:";

    /**
     * 槽位键缺省值：模型未给出槽位时使用，保证同一类型的信息仍能聚到同一槽位做冲突校验
     */
    public static final String DEFAULT_MEMORY_KEY = "general";

    /**
     * 主体键最大长度（与 DDL 中 subject_key 的 VARCHAR(80) 对齐）
     */
    public static final int SUBJECT_KEY_MAX_LEN = 80;
}
