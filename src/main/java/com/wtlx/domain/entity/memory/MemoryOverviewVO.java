package com.wtlx.domain.entity.memory;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 记忆全景视图：把"会话内短期记忆"与"跨会话长期记忆"并排呈现。
 * <p>
 * 存在的意义是让两层记忆的边界<b>可观测</b>。它们容易混淆——都叫"记忆"，
 * 都在支撑同一场对话，但作用域、载体、生命周期、检索方式完全不同。
 * 管理接口把它们放在同一个响应里对照，一眼就能看出：
 * 换一个 threadId，{@code session} 全空、{@code longTerm} 不变——这就是"跨会话复用"的直接证据。
 *
 * @author 金小梁
 */
@Data
public class MemoryOverviewVO {

    /**
     * 用户 id（长期记忆作用域）；本接口已要求登录，恒为当前用户
     */
    private Long userId;

    /**
     * 长期记忆命中明细（本次按 query 检索的结果）
     */
    private List<MemoryHit> hits;

    /**
     * 短期记忆视图（作用域 = 当前会话）
     */
    private SessionScope session;

    /**
     * 长期记忆视图（作用域 = 当前用户）
     */
    private LongTermScope longTerm;

    /**
     * 会话内短期记忆
     */
    @Data
    public static class SessionScope {

        /**
         * 会话 id（threadId 派生）
         */
        private String conversationId;

        /**
         * 压缩摘要；未触发压缩时为 null
         */
        private String summary;

        /**
         * 摘要覆盖的消息数
         */
        private Integer summaryMessageCount;

        /**
         * 当前会话消息总数
         */
        private int messageCount;

        /**
         * 用户发言轮次数，达到阈值会触发摘要压缩
         */
        private int userTurns;

        /**
         * 最近若干条消息（用于确认"这一轮在聊什么"）
         */
        private List<MessagePreview> recentMessages;
    }

    /**
     * 消息预览
     */
    @Data
    public static class MessagePreview {

        private String role;

        private String content;

        private String time;
    }

    /**
     * 跨会话长期记忆
     */
    @Data
    public static class LongTermScope {

        /**
         * 生效记忆总数
         */
        private int activeCount;

        /**
         * 按类型分布，直观回答"系统记住了什么"
         */
        private Map<String, Integer> countByType;

        /**
         * 本次检索命中的条数
         */
        private int hitCount;
    }
}
