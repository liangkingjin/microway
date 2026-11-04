package com.wtlx.service;

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
     * 先落库用户消息。
     * <p>
     * 与助手回复分开保存，是因为两者的"确定时刻"不同：用户消息在请求一进来就已成事实，
     * 而回复要等生成结束。若两者绑在一起存，用户在生成期间离开（连接断开、流被取消），
     * 回复为空时连问题一起丢掉——重新进入会话会看到"问题凭空消失"。
     *
     * @param conversationId 对话ID
     * @param userMessage    用户消息
     */
    void appendUserMessage(String conversationId, String userMessage);

    /**
     * 追加助手回复并检查是否需要压缩
     *
     * @param conversationId     对话ID
     * @param assistantReply     助手回复正文
     * @param assistantCardsJson 助手回复附带的检索卡片 JSON；无卡片时传 null
     */
    void appendAssistantMessage(String conversationId, String assistantReply,
                                String assistantCardsJson);

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
