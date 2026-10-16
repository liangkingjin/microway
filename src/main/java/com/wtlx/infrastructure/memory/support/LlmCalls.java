package com.wtlx.infrastructure.memory.support;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * 一次性 LLM 文本调用：封装"system + user 提示、取回文本"的样板，
 * 供抽取子 Agent、冲突判定器等所有"调一次模型拿一段文本"的场景共用，避免各处重复拼 Prompt。
 * <p>
 * 调用失败由调用方统一捕获（模型限流 / 超时属于预期内的降级），本方法不吞异常。
 */
public final class LlmCalls {

    private LlmCalls() {
    }

    public static String text(ChatModel model, String systemPrompt, String userPayload) {
        return model.call(new Prompt(List.of(
                        new SystemMessage(systemPrompt), new UserMessage(userPayload))))
                .getResult().getOutput().getText();
    }
}
