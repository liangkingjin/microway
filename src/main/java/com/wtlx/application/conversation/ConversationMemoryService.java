package com.wtlx.application.conversation;

import com.wtlx.domain.entity.conversation.ConversationMessage;
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
     * @param conversationId     对话ID
     * @param userMessage        用户消息
     * @param assistantReply     助手回复正文
     * @param assistantCardsJson 助手回复附带的检索卡片 JSON；无卡片时传 null
     */
    void appendAndCompressIfNeeded(String conversationId, String userMessage,
                                   String assistantReply, String assistantCardsJson);

    /**
     * 加载最近的消息用于前端展示（含检索卡片，与模型上下文无关）
     *
     * @param conversationId 对话ID
     * @param limit          最多返回的条数
     * @return 按时间正序排列的消息列表
     */
    List<ConversationMessage> loadRecentMessages(String conversationId, int limit);

    /**
     * 清除对话记忆
     *
     * @param conversationId 对话ID
     */
    void clear(String conversationId);
}
