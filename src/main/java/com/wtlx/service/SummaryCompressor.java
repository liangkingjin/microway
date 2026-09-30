package com.wtlx.service;

import com.wtlx.domain.entity.conversation.ConversationMessage;

import java.util.List;

/**
 * 摘要压缩服务接口
 * 负责将多轮对话压缩为简洁摘要
 */
public interface SummaryCompressor {

    /**
     * 压缩对话为摘要
     *
     * @param oldSummary 历史摘要（可为null）
     * @param messages   需要压缩的消息列表
     * @return 新摘要
     */
    String compress(String oldSummary, List<ConversationMessage> messages);
}
