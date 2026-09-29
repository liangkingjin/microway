package com.wtlx.service.impl;

import com.wtlx.entity.conversation.ConversationMessage;
import com.wtlx.entity.conversation.ConversationSummary;
import com.wtlx.entity.conversation.enums.MessageRole;
import com.wtlx.mapper.ConversationMessageMapper;
import com.wtlx.mapper.ConversationSummaryMapper;
import com.wtlx.service.ConversationMemoryService;
import com.wtlx.service.SummaryCompressor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 对话记忆服务实现
 * 两层记忆：历史消息 + 摘要压缩
 *
 * 加载流程：
 * 1. 并行加载摘要和历史消息
 * 2. 摘要 → SystemMessage
 * 3. 历史 → List<UserMessage/AssistantMessage>
 * 4. 合并返回 [摘要] + [历史]
 *
 * 压缩流程：
 * 1. 追加新消息
 * 2. 检查用户消息数是否达到阈值
 * 3. 达到阈值则异步调用LLM生成新摘要
 */
@Slf4j
@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final int SUMMARY_START_TURNS = 9;
    private static final int HISTORY_KEEP_TURNS = 8;

    @Resource
    private ConversationMessageMapper messageMapper;

    @Resource
    private ConversationSummaryMapper summaryMapper;

    @Resource
    private SummaryCompressor summaryCompressor;

    @Override
    public List<Message> loadContext(String conversationId) {
        // 并行加载摘要和历史消息
        CompletableFuture<ConversationSummary> summaryFuture = CompletableFuture.supplyAsync(() ->
                summaryMapper.findByConversationId(conversationId));

        CompletableFuture<List<ConversationMessage>> historyFuture = CompletableFuture.supplyAsync(() -> {
            List<ConversationMessage> recent = messageMapper.findRecentByConversationId(
                    conversationId, HISTORY_KEEP_TURNS * 2);
            Collections.reverse(recent);
            return recent;
        });

        ConversationSummary summary = summaryFuture.join();
        List<ConversationMessage> history = historyFuture.join();

        // 合并结果
        List<Message> messages = new ArrayList<>();

        // 1. 摘要 → SystemMessage
        if (summary != null && summary.getSummary() != null && !summary.getSummary().isBlank()) {
            messages.add(new SystemMessage("对话历史摘要：\n" + summary.getSummary()));
        }

        // 2. 历史 → UserMessage/AssistantMessage
        for (ConversationMessage msg : history) {
            switch (msg.getRole()) {
                case USER -> messages.add(new UserMessage(msg.getContent()));
                case ASSISTANT -> messages.add(new AssistantMessage(msg.getContent()));
                default -> log.debug("忽略非用户/助手角色的消息: {}", msg.getRole());
            }
        }

        log.debug("加载对话上下文, conversationId: {}, 摘要: {}, 历史消息: {}",
                conversationId, summary != null, history.size());
        return messages;
    }

    @Override
    public void appendAndCompressIfNeeded(String conversationId, String userMessage, String assistantReply) {
        // 1. 保存用户消息
        saveMessage(conversationId, MessageRole.USER, userMessage);
        // 2. 保存助手回复
        saveMessage(conversationId, MessageRole.ASSISTANT, assistantReply);

        // 3. 异步检查是否需要压缩
        CompletableFuture.runAsync(() -> doCompressIfNeeded(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        messageMapper.deleteByConversationId(conversationId);
        summaryMapper.deleteByConversationId(conversationId);
        log.info("已清除对话记忆, conversationId: {}", conversationId);
    }

    /**
     * 保存单条消息
     */
    private void saveMessage(String conversationId, MessageRole role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(java.time.LocalDateTime.now());
        messageMapper.insert(message);
    }

    /**
     * 执行压缩检查
     */
    private void doCompressIfNeeded(String conversationId) {
        try {
            // 1. 统计用户消息总数
            int totalUserMessages = messageMapper.countUserMessages(conversationId);

            // 2. 未达到阈值，跳过
            if (totalUserMessages < SUMMARY_START_TURNS) {
                return;
            }

            // 3. 获取已有摘要
            ConversationSummary existingSummary = summaryMapper.findByConversationId(conversationId);

            // 4. 获取所有消息，确定需要压缩的范围
            List<ConversationMessage> allMessages = messageMapper.findByConversationId(conversationId);
            int cutoffIndex = allMessages.size() - HISTORY_KEEP_TURNS * 2;
            if (cutoffIndex <= 0) {
                return;
            }

            // 5. 压缩范围：从开头到 cutoff（保留最近的 HISTORY_KEEP_TURNS 轮）
            List<ConversationMessage> messagesToCompress = allMessages.subList(0, cutoffIndex);

            if (messagesToCompress.isEmpty()) {
                return;
            }

            // 6. 调用LLM生成新摘要
            String oldSummaryText = (existingSummary != null) ? existingSummary.getSummary() : null;
            String newSummary = summaryCompressor.compress(oldSummaryText, messagesToCompress);

            // 7. 持久化摘要
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (existingSummary != null) {
                existingSummary.setSummary(newSummary);
                existingSummary.setMessageCount(existingSummary.getMessageCount() + messagesToCompress.size());
                existingSummary.setUpdatedAt(now);
                summaryMapper.update(existingSummary);
            } else {
                ConversationSummary newEntity = new ConversationSummary();
                newEntity.setConversationId(conversationId);
                newEntity.setSummary(newSummary);
                newEntity.setMessageCount(messagesToCompress.size());
                newEntity.setCreatedAt(now);
                newEntity.setUpdatedAt(now);
                summaryMapper.insert(newEntity);
            }

            // 8. 删除已压缩的消息（可选，也可以保留用于审计）
            messageMapper.deleteByConversationIdBefore(conversationId,
                    messagesToCompress.get(messagesToCompress.size() - 1).getCreatedAt());

            log.info("对话摘要压缩完成, conversationId: {}, 压缩消息数: {}, 摘要长度: {}",
                    conversationId, messagesToCompress.size(), newSummary.length());

        } catch (Exception e) {
            log.error("对话摘要压缩失败, conversationId: {}", conversationId, e);
        }
    }
}
