package com.wtlx.service.impl;

import com.wtlx.entity.conversation.ConversationMessage;
import com.wtlx.service.SummaryCompressor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 摘要压缩服务实现
 * 使用LLM将多轮对话压缩为简洁摘要
 */
@Slf4j
@Service
public class SummaryCompressorImpl implements SummaryCompressor {

    private static final int MAX_SUMMARY_CHARS = 200;

    private static final String COMPRESS_PROMPT = """
            你是一个对话摘要助手，负责将多轮对话压缩为简洁摘要。
            
            要求：
            1. 严格≤{maxChars}字符
            2. 仅一行，不换行
            3. 保留关键信息：用户目的地、偏好、预算、已确认的行程安排
            4. 语言简洁精炼
            """;

    private static final String MERGE_PROMPT = """
            历史摘要（仅用于合并去重，不得作为事实新增来源；若与本轮对话冲突，以本轮对话为准）：
            {oldSummary}
            
            请合并以上历史摘要与本轮对话，去重后输出更新摘要。
            要求：严格≤{maxChars}字符；仅一行。
            """;

    @Resource
    private ChatModel chatModel;

    @Override
    public String compress(String oldSummary, List<ConversationMessage> messages) {
        // 构建对话内容
        String conversationText = messages.stream()
                .map(m -> m.getRole().getCode() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String promptText;
        if (oldSummary != null && !oldSummary.isBlank()) {
            // 有历史摘要，需要合并
            promptText = MERGE_PROMPT
                    .replace("{oldSummary}", oldSummary)
                    .replace("{maxChars}", String.valueOf(MAX_SUMMARY_CHARS));
        } else {
            // 无历史摘要，直接生成
            promptText = COMPRESS_PROMPT
                    .replace("{maxChars}", String.valueOf(MAX_SUMMARY_CHARS));
        }

        String fullPrompt = promptText + "\n\n对话内容：\n" + conversationText;

        try {
            String result = chatModel.call(new Prompt(fullPrompt))
                    .getResult()
                    .getOutput()
                    .getText();

            // 截断超长摘要
            if (result != null && result.length() > MAX_SUMMARY_CHARS) {
                result = result.substring(0, MAX_SUMMARY_CHARS);
            }

            log.debug("摘要压缩完成, 原始长度: {}, 压缩后: {}", conversationText.length(), result.length());
            return result;
        } catch (Exception e) {
            log.error("摘要压缩LLM调用失败", e);
            // 降级：返回简单的消息计数摘要
            return String.format("已进行%d轮对话，讨论了旅游规划相关内容。", messages.size() / 2);
        }
    }
}
