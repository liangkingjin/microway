package com.wtlx.infrastructure.memory.support;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 模型结构化输出的文本容错工具。
 * <p>
 * 记忆链路里有<b>两处</b>要解析模型返回的 JSON（抽取子 Agent 的候选数组、
 * 冲突判定器的裁决对象），它们的容错诉求完全一致：模型偶发加 Markdown 围栏、
 * 前置一句寒暄、或在字段里塞进非字符串字面量。这类"和模型输出格式较劲"的胶水代码
 * 一旦复制成两份，就会各自演化——修了抽取路径忘了裁决路径，表现为"冲突判定偶发失效"。
 * 因此集中在此，两份调用方共享同一套容错口径。
 *
 * @author 金小梁
 */
public final class JsonTexts {

    /**
     * 代码围栏起始标记
     */
    private static final String FENCE = "```";

    private JsonTexts() {
    }

    /**
     * 剥离 Markdown 代码围栏（{@code ```json ... ```} 或 {@code ``` ... ```}）。
     * <p>
     * 无围栏时原样返回（仅 trim），因此调用方无需先判断是否带围栏。
     *
     * @param raw 模型原始输出
     * @return 去掉围栏后的文本；入参为 null 时返回空串
     */
    public static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith(FENCE)) {
            return text;
        }
        // 丢掉首行围栏标记（可能带语言标识，如 ```json）
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return "";
        }
        text = text.substring(firstNewline + 1);
        int fenceEnd = text.lastIndexOf(FENCE);
        if (fenceEnd >= 0) {
            text = text.substring(0, fenceEnd);
        }
        return text.trim();
    }

    /**
     * 读取字段为字符串：字段缺失/为 null 返回 null，非字符串字面量（对象、数字、数组）
     * 退化为其 JSON 文本，避免调用方为"模型偶尔不按 schema 输出"各写一套分支。
     *
     * @param node  对象节点
     * @param field 字段名
     * @return 字段文本；不存在时返回 null
     */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }
}
