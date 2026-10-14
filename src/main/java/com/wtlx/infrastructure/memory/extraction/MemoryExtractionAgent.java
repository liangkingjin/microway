package com.wtlx.infrastructure.memory.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.domain.entity.memory.MemoryCandidate;
import com.wtlx.domain.entity.memory.enums.MemoryType;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆抽取子 Agent：把一轮对话压缩为结构化候选。
 * <p>
 * <b>它只有"读"的权限，没有"写"的权限</b>——这是整套体系的安全边界。
 * 模型再不可靠，最坏结果也只是产出一个奇怪候选，而候选还要过合并 / 冲突校验才可能落库；
 * 若让模型直接决定写什么、覆盖什么，一次幻觉就能永久篡改用户画像。
 * <p>
 * 输出是严格 JSON 数组，解析采用"先容错、再严格"的策略：模型偶发加 Markdown 围栏、
 * 前置寒暄、或把数组包在对象里，都需要被正确剥出来，否则一条格式抖动就等于整轮抽取失败。
 * <p>
 * 与主对话共用同一个模型实例（{@code openAiChatModel}，百炼兼容模式端点），
 * 不额外引入模型配置，避免 Key / 端点重复维护。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryExtractionAgent {

    /**
     * 抽取提示词。
     * <p>
     * 关键设计：
     * <ul>
     *     <li><b>划定负样本</b>——明确列出"不要抽什么"比只列"要抽什么"有效得多，
     *         尤其要挡住"把助手建议当用户偏好"这一类污染；</li>
     *     <li><b>槽位白名单 + 兜底</b>——给出既有槽位让同义信息收敛，同时允许新槽位，
     *         否则模型会硬塞进不匹配的槽位，反而制造假冲突；</li>
     *     <li><b>相对时间必须绝对化</b>——"下个月去成都"若不换算成日期，
     *         记忆将永远有效，过期清理就彻底失效；</li>
     *     <li><b>空数组是合法答案</b>——明说"没有就输出 []"，否则模型倾向于硬凑，
     *         这是噪声记忆的主要来源。</li>
     * </ul>
     */
    private static final String EXTRACTION_SYSTEM_PROMPT = """
            你是旅游平台「微途旅行」的用户长期记忆抽取器。任务：从一轮对话中抽取"值得跨会话长期记住"的\
            用户个性化信息，输出严格 JSON 数组。

            ## 应该抽取（跨会话仍然成立的信息）
            - 偏好：喜欢什么样的目的地 / 玩法 / 节奏
            - 避讳：不喜欢、要避开的东西
            - 硬性约束：不可协商的条件（同行人、时间限制、身体条件、预算上限）
            - 兴趣：摄影、徒步、观星、美食、历史等
            - 旅行风格：穷游 / 深度游 / 打卡 / 休闲
            - 饮食：忌口、口味、素食、清真等
            - 住宿 / 交通偏好
            - 同行情况：独自 / 情侣 / 亲子 / 朋友团
            - 预算档位
            - 明确的出行计划（带时间）

            ## 禁止抽取
            - 寒暄、情绪、对助手回答的复述或赞同
            - 助手自己推荐的方案、景点、路线（除非用户明确表示认同或已决定）
            - 通用常识、与用户个人无关的知识
            - 本轮问题本身（"成都三日游怎么安排"只是提问，不是偏好）
            - 用户明确说"随便""都行""你决定"这类无信息量的内容

            ## 槽位键（key）规范
            优先复用以下既有槽位，语义不匹配时才自拟（ASCII、点分、全小写）：
            destination.prefer, travel.style, trip.pace, interest.tag, avoid.prefer,
            budget.level, party.companion, dietary.restriction, accommodation.prefer,
            transport.prefer, season.prefer, trip.plan

            ## 字段规则
            - type：PREFERENCE / DISLIKE / CONSTRAINT / INTEREST / TRAVEL_STYLE / DIETARY /
              ACCOMMODATION / TRANSPORT / PARTY / BUDGET / TRIP_PLAN / FACT
            - content：一句完整的中文事实陈述，主语为"用户"，例如"用户喜欢徒步与自然风光"
            - structured：可结构化时给出键值对象，否则 null
            - confidence：0~1，表示"这是用户长期稳定的真实偏好"的把握程度
            - importance：0~1，表示该信息对行程规划决策的重要性
            - validTo：有时效的信息（"下个月""明年暑假""这次五一"）必须换算成绝对时间，
              格式 yyyy-MM-ddTHH:mm:ss；长期稳定的信息填 null
            - evidence：从用户原话中截取的不超过 60 字的依据片段

            ## 输出
            只输出 JSON 数组本身，不要解释、不要 Markdown 代码围栏。没有任何可抽取信息时输出 []。
            示例：
            [{"type":"PREFERENCE","key":"travel.style","content":"用户喜欢徒步与自然风光，不喜欢人多拥挤的景点",\
            "structured":{"style":"自然","avoid":"拥挤"},"confidence":0.85,"importance":0.75,"validTo":null,\
            "evidence":"我喜欢徒步，最好人少一点"}]
            """;

    /**
     * 单次抽取结果里候选条数的硬上限，防止模型异常刷屏
     */
    private static final int HARD_CANDIDATE_LIMIT = 20;

    @Resource
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MemoryProperties memoryProperties;

    /**
     * 从一轮对话中抽取记忆候选
     *
     * @param userMessage    用户本轮发言
     * @param assistantReply 助手本轮回复（用于排除"助手建议被误当偏好"）
     * @param sessionDigest  会话上下文摘要（可为 null），帮助模型判断这句话是不是在回应上一轮
     * @return 候选列表；无候选或抽取失败时返回空列表（失败不抛异常，由调用方记台账）
     */
    public List<MemoryCandidate> extract(String userMessage, String assistantReply, String sessionDigest) {
        if (!StringUtils.hasText(userMessage)) {
            return List.of();
        }
        try {
            String userPayload = buildUserPayload(userMessage, assistantReply, sessionDigest);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(EXTRACTION_SYSTEM_PROMPT),
                    new UserMessage(userPayload)));

            String raw = chatModel.call(prompt).getResult().getOutput().getText();
            List<MemoryCandidate> candidates = parse(raw);
            log.debug("记忆抽取完成，原始输出长度 {}，解析候选 {} 条", raw == null ? 0 : raw.length(), candidates.size());
            return candidates;
        } catch (Exception e) {
            log.warn("记忆抽取失败，本轮不产生长期记忆: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 拼接本轮对话载荷。带上当前时间是为了让模型把"下个月"这类相对时间换算成绝对日期
     */
    private String buildUserPayload(String userMessage, String assistantReply, String sessionDigest) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                .append('\n');
        if (StringUtils.hasText(sessionDigest)) {
            sb.append("\n【会话背景摘要】\n").append(sessionDigest.trim()).append('\n');
        }
        sb.append("\n【本轮用户发言】\n").append(userMessage.trim()).append('\n');
        if (StringUtils.hasText(assistantReply)) {
            // 截断助手回复：它的作用是提供上下文与"不要抽取"的对照，不需要全文
            String reply = assistantReply.trim();
            sb.append("\n【助手回复（仅供参考，禁止作为用户偏好来源）】\n")
                    .append(reply.length() > 800 ? reply.substring(0, 800) + "..." : reply)
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 解析模型输出为候选列表。
     * <p>
     * 容错顺序：去 Markdown 围栏 → 定位最外层 JSON 数组（或对象里的 memories/candidates 字段）→ 逐项转换。
     * 任何一项字段非法都只丢该项，不影响整批
     */
    private List<MemoryCandidate> parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String json = extractJsonBlock(raw);
        if (json == null) {
            log.debug("记忆抽取输出未找到 JSON 结构，按无候选处理");
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arrayNode = locateArray(root);
            if (arrayNode == null || !arrayNode.isArray()) {
                return List.of();
            }
            List<MemoryCandidate> candidates = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                if (candidates.size() >= HARD_CANDIDATE_LIMIT) {
                    log.warn("记忆抽取候选数超过硬上限 {}，截断", HARD_CANDIDATE_LIMIT);
                    break;
                }
                MemoryCandidate candidate = toCandidate(node);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            return candidates;
        } catch (Exception e) {
            log.warn("记忆抽取输出解析失败，按无候选处理: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 剥离 Markdown 围栏与前置说明，截出最外层 JSON 片段
     */
    private String extractJsonBlock(String raw) {
        String text = raw.trim();
        // 去掉 ```json ... ``` 围栏
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.trim();
        }
        int arrayStart = text.indexOf('[');
        int objectStart = text.indexOf('{');
        int start;
        char open;
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            start = arrayStart;
            open = '[';
        } else if (objectStart >= 0) {
            start = objectStart;
            open = '{';
        } else {
            return null;
        }
        char close = open == '[' ? ']' : '}';
        int end = text.lastIndexOf(close);
        if (end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * 从根节点定位候选数组：既接受裸数组，也接受 {@code {"memories":[...]}} / {@code {"candidates":[...]}} 包装
     */
    private JsonNode locateArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        for (String field : new String[]{"memories", "candidates", "data", "list"}) {
            JsonNode node = root.get(field);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    /**
     * 单条候选转换；关键字段缺失时返回 null 丢弃
     */
    private MemoryCandidate toCandidate(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String content = text(node, "content");
        if (!StringUtils.hasText(content) || content.trim().length() < 4) {
            return null;
        }
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.setType(MemoryType.fromCode(text(node, "type")));
        candidate.setKey(text(node, "key"));
        candidate.setContent(content.trim());
        candidate.setEvidence(text(node, "evidence"));

        JsonNode structuredNode = node.get("structured");
        if (structuredNode != null && structuredNode.isObject() && !structuredNode.isEmpty()) {
            candidate.setStructured(structuredNode.toString());
        }

        candidate.setConfidence(clamp(number(node, "confidence", 0.6D)));
        candidate.setImportance(clamp(number(node, "importance", 0.5D)));
        candidate.setValidTo(parseDateTime(text(node, "validTo")));

        if (candidate.getConfidence() < memoryProperties.getExtractionMinConfidence()) {
            log.debug("候选置信度 {} 低于阈值，丢弃: {}", candidate.getConfidence(), candidate.getContent());
            return null;
        }
        return candidate;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private double number(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    /**
     * 解析有效期：同时兼容 {@code yyyy-MM-ddTHH:mm:ss} 与 {@code yyyy-MM-dd}，非法值按长期有效处理
     */
    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            if (normalized.length() <= 10) {
                return LocalDate.parse(normalized).atStartOfDay();
            }
            // 去掉时区与毫秒（模型可能给出 2026-12-31T00:00:00+08:00）
            if (normalized.length() > 19) {
                normalized = normalized.substring(0, 19);
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            log.debug("有效期解析失败，按长期有效处理: {}", value);
            return null;
        }
    }
}
