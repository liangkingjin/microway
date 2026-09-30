package com.wtlx.domain.entity.conversation;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话摘要实体
 */
@Data
public class ConversationSummary {

    private Long id;

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * 摘要内容
     */
    private String summary;

    /**
     * 摘要覆盖的消息数量
     */
    private Integer messageCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
