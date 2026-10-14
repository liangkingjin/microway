package com.wtlx.infrastructure.memory.merge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.domain.entity.memory.MemoryCandidate;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.enums.MemoryDecision;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.support.JsonTexts;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 记忆冲突判定器（LLM）。
 * <p>
 * 只在"规则无法裁决的灰区"被调用：同槽位取值不同、或语义相似度落在重复线与冲突线之间。
 * 高相似（明显重复）与低相似（明显无关）都由规则直接定案，因此真实的 LLM 调用比例远低于
 * 抽取次数——这是成本可控的关键。
 * <p>
 * 判定维度与人类整理笔记一致：<b>是不是同一件事</b>、<b>是不是互相矛盾</b>、
 * <b>新的是否更具体</b>、<b>用户是否在否定旧说法</b>。
 * 判定结果必须带 {@code target}（针对哪条已有记忆），否则 {@code SUPERSEDE} 无从落地。
 * <p>
 * 失败兜底：判定失败返回 null，由调用方退化为保守规则（同槽位即 SUPERSEDE，否则 ADD）。
 * 保守规则偏向"以新信息为准"，符合"用户最近说的话更可信"的常识。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryConflictJudge {

    /**
     * 冲突判定提示词。
     * <p>
     * 刻意强调"不要因为话题相关就判冲突"——模型最容易犯的错是把
     * "喜欢徒步" 和 "预算 3000" 这类同域不同槽位的记忆判成矛盾，
     * 进而错误覆盖用户偏好。因此把"同一槽位 / 同一件事"作为判定的前置条件写死在提示里。
     */
    private static final String JUDGE_SYSTEM_PROMPT = """
            你是用户长期记忆库的冲突仲裁器。给定【新信息】和若干【已有记忆】，判断新信息应该如何入库。

            判定前提：只有"针对同一件事 / 同一槽位"的信息才可能互相冲突。
            话题相关（都属于旅游）不等于冲突，例如"喜欢徒步"与"预算 3000"是两个独立事实，互不冲突。

            可选决策（必须选一个）：
            - NOOP：新信息与某条已有记忆表达的是同一个事实，只是措辞不同，且没有补充新细节
            - UPDATE：针对同一件事，新信息是已有记忆的补充或细化，两者不矛盾（例如"喜欢徒步"→"喜欢徒步，尤其高海拔线路"）
            - SUPERSEDE：针对同一件事，新信息与已有记忆的取值矛盾，用户的说法变了（例如"喜欢辣"→"不吃辣了"）
            - FORGET：用户明确否定了某条已有记忆，且这条新信息本身不需要留下（例如"我上次说喜欢人多热闹，那是我随便说的"）
            - ADD：新信息与所有已有记忆都不针对同一件事，是全新的信息

            注意：用户最新表述优先。若新信息更具体、更明确，倾向 UPDATE；若明确改口，倾向 SUPERSEDE。

            只输出 JSON 对象，不要解释、不要代码围栏：
            {"decision":"SUPERSEDE","target":2,"reason":"用户明确改口，不再接受辣味餐食","merged":null}
            - target：所针对的已有记忆序号（1 开始）；ADD 时填 null
            - merged：仅 UPDATE 需要，给出合并后的完整中文陈述；其余填 null
            """;

    @Resource
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MemoryProperties memoryProperties;

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

    /**
     * 判定候选与已有记忆的合并方式
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
            String payload = buildPayload(candidate, existing);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(JUDGE_SYSTEM_PROMPT),
                    new UserMessage(payload)));
            String raw = chatModel.call(prompt).getResult().getOutput().getText();
            return parse(raw, existing.size());
        } catch (Exception e) {
            log.warn("记忆冲突判定失败，退化为保守规则: {}", e.getMessage());
            return null;
        }
    }

    private String buildPayload(MemoryCandidate candidate, List<ScoredMemory> existing) {
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
            sb.append(i + 1).append(". [").append(memory.getMemoryType()).append("] ")
                    .append("槽位=").append(memory.getMemoryKey())
                    .append("，内容=").append(memory.getContent())
                    .append("（置信 ").append(String.format("%.2f", memory.getConfidence()))
                    .append("，相似度 ").append(String.format("%.2f", existing.get(i).pathScore()))
                    .append("）\n");
        }
        return sb.toString();
    }

    private Verdict parse(String raw, int existingCount) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String json = extractJsonObject(raw);
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

    /**
     * 截出最外层的 {@code { }} 片段；围栏剥离复用 {@link JsonTexts}，与抽取侧同一套口径
     */
    private String extractJsonObject(String raw) {
        String text = JsonTexts.stripCodeFence(raw);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
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
}
