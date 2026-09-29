package com.wtlx.entity.conversation.enums;

import org.springframework.ai.chat.messages.MessageType;

/**
 * 消息角色枚举
 * <p>
 * 统一表达对话消息的角色，替代散落的 "USER"/"ASSISTANT" 等魔法字符串：
 * <ul>
 *     <li>作为数据库 conversation_message.role 列的持久化编码（{@link #getCode()}）</li>
 *     <li>与 Spring AI 的 {@link MessageType} 互相转换，便于构建 Chat 消息</li>
 * </ul>
 */
public enum MessageRole {

    USER("USER", "用户"),
    ASSISTANT("ASSISTANT", "助手"),
    SYSTEM("SYSTEM", "系统"),
    TOOL("TOOL", "工具");

    /**
     * 持久化编码，与数据库存储值、Spring AI MessageType 名称保持一致
     */
    private final String code;

    /**
     * 角色中文描述
     */
    private final String description;

    MessageRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据持久化编码解析消息角色
     *
     * @param code 角色编码（大小写不敏感）
     * @return 对应的消息角色
     */
    public static MessageRole fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("消息角色编码不能为空");
        }
        String normalized = code.trim().toUpperCase();
        for (MessageRole role : values()) {
            if (role.code.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的消息角色编码: " + code);
    }

    /**
     * Spring AI MessageType 转换为 MessageRole
     */
    public static MessageRole fromMessageType(MessageType messageType) {
        if (messageType == null) {
            throw new IllegalArgumentException("MessageType 不能为空");
        }
        return fromCode(messageType.name());
    }

    /**
     * MessageRole 转换为 Spring AI MessageType
     */
    public MessageType toMessageType() {
        return MessageType.valueOf(this.code);
    }
}
