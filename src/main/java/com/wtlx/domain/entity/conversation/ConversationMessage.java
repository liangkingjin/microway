package com.wtlx.domain.entity.conversation;

import com.wtlx.domain.entity.conversation.enums.MessageRole;
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
     * 助手消息附带的检索卡片（TripCardVO 的 JSON 数组）。
     * 用户消息为 null。用于重新进入会话时恢复"文字正文 + 游记卡片"的混合展示。
     */
    private String cardsJson;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
