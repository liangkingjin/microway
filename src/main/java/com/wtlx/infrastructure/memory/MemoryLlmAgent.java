package com.wtlx.infrastructure.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.memory.MemoryCandidate;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.enums.MemoryDecision;
import com.wtlx.domain.entity.memory.enums.MemoryType;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.support.JsonTexts;
import com.wtlx.infrastructure.memory.support.LlmCalls;
import com.wtlx.infrastructure.memory.support.MemoryPrompts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆 LLM Agent：统一承担两项 LLM 职责——<b>记忆抽取</b>与<b>冲突校验</b>。
 * <p>
 * 此前二者分属 {@code MemoryExtractionAgent} 与 {@code MemoryConflictJudge} 两个独立组件，
 * 共用同一模型实例、同一套 JSON 容错口径，却各写一遍。合并后：
 * <ul>
 *     <li>共享字段（模型 / ObjectMapper / 配置）与 JSON 解析辅助方法只保留一份；</li>
 *     <li>提示词统一收口到 {@link MemoryPrompts}，按用途分组；</li>
 *     <li>职责边界仍是清晰的两组公开方法：{@link #extractSegment}（读，产出候选）与
 *         {@link #judge}（灰区裁决），彼此无耦合。</li>
 * </ul>
 * <p>
 * <b>安全边界</b>：本 Agent 只有"读"的权限，没有"写"的权限——它只产出候选或裁决意见，
 * 真正落库由 {@code MemoryMergeService} 把关。模型再不可靠，最坏也只是产出一个奇怪候选或一次保守裁决，
 * 而候选还要过合并闸门的指纹 / 规则级判定才可能落库。
 * <p>
 * 与主对话共用同一个模型实例（{@code openAiChatModel}，百炼兼容模式端点），
 * 不额外引入模型配置，避免 Key / 端点重复维护。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryLlmAgent {

    /**
     * 单次抽取结果里候选条数的硬上限，防止模型异常刷屏
     */
    private static final int HARD_CANDIDATE_LIMIT = 20;

    /**
     * 单条记忆内容长度上限：模型偶发吐长文本会撑爆注入预算并拖长向量化，硬截断兜底
     */
    private static final int MAX_CONTENT_LEN = 200;

    @Resource
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MemoryProperties memoryProperties;

    /**
     * 从一段对话记录中抽取记忆候选。
     * <p>
     * 输入是若干轮（用户+助手）的完整往返，由 LLM 综合整段对话自行判断是否需要抽取——
     * 这正是"提取完全基于 LLM、由 LLM 判断是否需要提取"的落地：启发式前置过滤被移除，
     * 模型认为没有可沉淀信息时直接返回空列表，调用方据此跳过后续合并。
     *
     * @param segment 一段对话记录（按时间正序，最多约 10 轮）
     * @return 候选列表；无候选或抽取失败时返回空列表（失败不抛异常，由调用方记台账）
     */
    public List<MemoryCandidate> extractSegment(List<ConversationMessage> segment) {
        if (segment == null || segment.isEmpty()) {
            return List.of();
        }
        try {
            String userPayload = buildSegmentPayload(segment);
            String raw = LlmCalls.text(chatModel, MemoryPrompts.EXTRACTION_SYSTEM_PROMPT, userPayload);
            List<MemoryCandidate> candidates = parseExtraction(raw);
            log.debug("记忆段抽取完成，原始输出长度 {}，解析候选 {} 条", raw == null ? 0 : raw.length(), candidates.size());
            return candidates;
        } catch (Exception e) {
            log.warn("记忆段抽取失败，本段不产生长期记忆: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 判定候选与已有记忆的合并方式。
     * <p>
     * 只在"规则无法裁决的灰区"被调用：同槽位取值不同、或语义相似度落在重复线与冲突线之间。
     * 高相似（明显重复）与低相似（明显无关）都由规则直接定案，因此真实的 LLM 调用比例远低于
     * 抽取次数——这是成本可控的关键。
     * <p>
     * 失败兜底：判定失败返回 null，由调用方退化为保守规则（同槽位即 SUPERSEDE，否则 ADD）。
     * 保守规则偏向"以新信息为准"，符合"用户最近说的话更可信"的常识。
     *
     * @param candidate 抽取出的新候选
     * @param existing  同主体下可能相关的已有记忆（同槽位 + 向量相似），非空
     * @return 判定结果；调用失败或开关关闭时返回 null
     */
    public Verdict judge(MemoryCandidate candidate, List<ScoredMemory> existing) {
        if (!memoryProperties.isConflictJudgeEnabled() || existing == null || existing.isEmpty()) {
            return null;
        }
        try {
            String payload = buildJudgePayload(candidate, existing);
            String raw = LlmCalls.text(chatModel, MemoryPrompts.CONFLICT_JUDGE_SYSTEM_PROMPT, payload);
            return parseVerdict(raw, existing.size());
        } catch (Exception e) {
            log.warn("记忆冲突判定失败，退化为保守规则: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判定结果
     *
     * @param decision      决策
     * @param targetIndex   所针对的已有记忆在入参列表中的下标（0 开始）；-1 表示无目标
     * @param reason        判定理由
     * @param mergedContent UPDATE 时的合并后内容，其余为 null
     */
    public record Verdict(MemoryDecision decision, int targetIndex, String reason, String mergedContent) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 抽取侧：载荷拼接 + 输出解析
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 拼接整段对话载荷。带上当前时间是为了让模型把"下个月"这类相对时间换算成绝对日期；
     * 助手发言以"禁止作为用户偏好来源"的角色出现，用于提供上下文与负样本对照
     */
    private String buildSegmentPayload(List<ConversationMessage> segment) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                .append('\n');
        sb.append("\n【一段对话记录（用户与助手的多轮往返，按时间正序）】\n");
        for (ConversationMessage message : segment) {
            if (message == null) {
                continue;
            }
            String roleLabel = message.getRole() != null && "ASSISTANT".equals(message.getRole().getCode())
                    ? "助手（仅供参考，禁止作为用户偏好来源）" : "用户";
            String content = message.getContent() == null ? "" : message.getContent();
            sb.append(roleLabel).append("：")
                    .append(content.length() > 600 ? content.substring(0, 600) + "..." : content)
                    .append('\n');
        }
        sb.append("\n请综合整段对话，判断是否需要抽取用户长期记忆并输出 JSON 数组。\n");
        return sb.toString();
    }

    /**
     * 解析模型输出为候选列表。
     * <p>
     * 容错顺序：去 Markdown 围栏 → 定位最外层 JSON 数组（或对象里的 memories/candidates 字段）→ 逐项转换。
     * 任何一项字段非法都只丢该项，不影响整批
     */
    private List<MemoryCandidate> parseExtraction(String raw) {
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
        String content = JsonTexts.text(node, "content");
        if (!StringUtils.hasText(content) || content.trim().length() < 4) {
            return null;
        }
        content = content.trim();
        if (content.length() > MAX_CONTENT_LEN) {
            content = content.substring(0, MAX_CONTENT_LEN);
        }
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.setType(MemoryType.fromCode(JsonTexts.text(node, "type")));
        candidate.setKey(JsonTexts.text(node, "key"));
        candidate.setContent(content);
        candidate.setEvidence(JsonTexts.text(node, "evidence"));

        JsonNode structuredNode = node.get("structured");
        if (structuredNode != null && structuredNode.isObject() && !structuredNode.isEmpty()) {
            candidate.setStructured(structuredNode.toString());
        }

        candidate.setConfidence(clamp(number(node, "confidence", 0.6D)));
        candidate.setImportance(clamp(number(node, "importance", 0.5D)));
        candidate.setValidTo(parseDateTime(JsonTexts.text(node, "validTo")));

        if (candidate.getConfidence() < memoryProperties.getExtractionMinConfidence()) {
            log.debug("候选置信度 {} 低于阈值，丢弃: {}", candidate.getConfidence(), candidate.getContent());
            return null;
        }
        return candidate;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 冲突校验侧：载荷拼接 + 输出解析
    // ─────────────────────────────────────────────────────────────────────────

    private String buildJudgePayload(MemoryCandidate candidate, List<ScoredMemory> existing) {
        StringBuilder sb = new StringBuilder();
        sb.append("【新信息】\n")
                .append("类型：").append(candidate.getType().getCode()).append('\n')
                .append("槽位：").append(candidate.getKey()).append('\n')
                .append("内容：").append(candidate.getContent()).append('\n');
        if (StringUtils.hasText(candidate.getEvidence())) {
            sb.append("用户原话：").append(candidate.getEvidence()).append('\n');
        }
        sb.append("\n【已有记忆】\n");
        for (int i = 0; i < existing.size(); i++) {
            var memory = existing.get(i).memory();
            // 同槽位记忆分数为 0（非向量召回），标成"同槽位命中"而不是误报相似度 0.00
            double score = existing.get(i).pathScore();
            String simText = score > 0 ? "相似度 " + String.format("%.2f", score) : "同槽位命中";
            sb.append(i + 1).append(". [").append(memory.getMemoryType()).append("] ")
                    .append("槽位=").append(memory.getMemoryKey())
                    .append("，内容=").append(memory.getContent())
                    .append("（置信 ").append(String.format("%.2f", memory.getConfidence()))
                    .append("，").append(simText)
                    .append("）\n");
        }
        return sb.toString();
    }

    private Verdict parseVerdict(String raw, int existingCount) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String json = extractJsonBlock(raw);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            MemoryDecision decision = MemoryDecision.fromCode(JsonTexts.text(node, "decision"));
            int targetIndex = index(node.get("target"), existingCount);
            String reason = JsonTexts.text(node, "reason");
            // merged 只在 UPDATE 有意义；其余决策与"给了空串"一律归一为 null，下游无需再做空值判断
            String rawMerged = JsonTexts.text(node, "merged");
            String merged = StringUtils.hasText(rawMerged) ? rawMerged.trim() : null;

            // 除 ADD 外都必须有明确目标，否则决策无法落地，退化为 ADD 更安全
            if (decision != MemoryDecision.ADD && targetIndex < 0) {
                log.debug("冲突判定未给出有效 target，退化为 ADD");
                return new Verdict(MemoryDecision.ADD, -1, "判定缺失目标，退化新增", null);
            }
            if (decision == MemoryDecision.ADD) {
                targetIndex = -1;
            }
            return new Verdict(decision, targetIndex, reason, merged);
        } catch (Exception e) {
            log.warn("冲突判定结果解析失败，退化为保守规则: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 两侧共用的 JSON / 取值辅助
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 截出最外层 JSON 片段（同时兼容 { } 与 [ ]）；围栏剥离复用 {@link JsonTexts}，
     * 与抽取 / 冲突校验两侧同一套口径，只负责"从整段文本里截出最外层 { } / [ ]"这一件事。
     */
    private String extractJsonBlock(String raw) {
        String text = JsonTexts.stripCodeFence(raw);
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
     * 解析 1 基序号为 0 基下标；超范围或缺失返回 -1
     */
    private int index(JsonNode value, int existingCount) {
        if (value == null || value.isNull()) {
            return -1;
        }
        int raw;
        if (value.isNumber()) {
            raw = value.asInt();
        } else {
            try {
                raw = Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        int zeroBased = raw - 1;
        return (zeroBased >= 0 && zeroBased < existingCount) ? zeroBased : -1;
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
