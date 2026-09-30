package com.wtlx.application.conversation;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 对话记忆服务接口
 * 实现两层记忆：历史消息 + 摘要压缩
 */
public interface ConversationMemoryService {

    /**
     * 加载对话上下文（摘要 + 历史消息）
     *
     * @param conversationId 对话ID
     * @return 消息列表，包含摘要（SystemMessage）+ 历史消息
     */
    List<Message> loadContext(String conversationId);

    /**
     * 追加新消息并检查是否需要压缩
     *
     * @param conversationId 对话ID
     * @param userMessage    用户消息
     * @param assistantReply 助手回复
     */
    void appendAndCompressIfNeeded(String conversationId, String userMessage, String assistantReply);

    /**
     * 清除对话记忆
     *
     * @param conversationId 对话ID
     */
    void clear(String conversationId);
}
