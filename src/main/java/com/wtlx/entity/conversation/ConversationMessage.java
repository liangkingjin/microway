package com.wtlx.entity.conversation;

import com.wtlx.entity.conversation.enums.MessageRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息实体
 */
@Data
public class ConversationMessage {

    private Long id;

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * 消息角色
     */
    private MessageRole role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
