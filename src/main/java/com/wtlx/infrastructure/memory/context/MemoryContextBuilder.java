package com.wtlx.infrastructure.memory.context;

import com.wtlx.domain.entity.memory.MemoryHit;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.retrieval.MemoryRetrievalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆上下文构建器：把检索命中渲染成可注入模型的系统提示。
 * <p>
 * 三个职责：
 * <ol>
 *     <li><b>冷热分层</b>——稳定层（长期有效的高置信画像）与时效层（带有效期的行程 / 预算）
 *         分开成段。稳定层是"这个人长期是什么样"，必须优先占额度；
 *         时效层是"这一次出行的安排"，价值随时间衰减，额度小、会自然退场。</li>
 *     <li><b>预算裁剪</b>——按字符数硬裁剪。记忆注入是"每轮都发生"的固定开销，
 *         不加预算就会随记忆增长不断推高 token 成本与首字延迟，最终挤占 RAG 结果的上下文空间。</li>
 *     <li><b>冲突优先级声明</b>——提示里明确写"若与本轮用户表述冲突，以本轮为准"。
 *         长期记忆天然滞后于用户的最新表达，不声明规则，模型可能坚持旧偏好而忽略用户刚说的话。</li>
 * </ol>
 * 渲染逻辑集中在此，注入链路（拦截器）与显式召回（工具）共用同一份格式，
 * 避免"主动注入"和"模型主动查询"看到两种不同表述的记忆。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryContextBuilder {

    /**
     * 时效信息的有效期展示格式
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private MemoryRetrievalService memoryRetrievalService;

    /**
     * 构建结果
     *
     * @param text 可直接注入系统提示的文本；无记忆时为 null
     * @param hits 本次命中（供调试与管理接口复用）
     */
    public record MemoryPrompt(String text, List<MemoryHit> hits) {

        public boolean isEmpty() {
            return text == null || text.isBlank();
        }

        public static MemoryPrompt empty() {
            return new MemoryPrompt(null, List.of());
        }
    }

    /**
     * 检索并渲染长期记忆
     *
     * @param subjectKey 记忆主体键
     * @param query      本轮用户查询（作为检索输入）
     * @return 渲染结果；无主体、开关关闭或无命中时返回空结果
     */
    public MemoryPrompt build(String subjectKey, String query) {
        List<MemoryHit> hits = memoryRetrievalService.retrieve(subjectKey, query);
        if (hits.isEmpty()) {
            return MemoryPrompt.empty();
        }
        String text = render(hits);
        if (!StringUtils.hasText(text)) {
            return MemoryPrompt.empty();
        }
        return new MemoryPrompt(text, hits);
    }

    /**
     * 渲染命中为提示文本（供注入与显式召回共用）
     *
     * @param hits 融合后的命中，已按融合分降序
     * @return 提示文本；无可用行时返回 null
     */
    public String render(List<MemoryHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }

        // 1. 冷热分层：稳定层优先占用额度
        List<MemoryHit> stable = new ArrayList<>();
        List<MemoryHit> ephemeral = new ArrayList<>();
        for (MemoryHit hit : hits) {
            if (hit.isStable()) {
                stable.add(hit);
            } else {
                ephemeral.add(hit);
            }
        }

        int budget = Math.max(200, memoryProperties.getContextMaxChars());
        StringBuilder sb = new StringBuilder(budget + 128);
        String header = "【用户长期记忆（跨会话沉淀）】\n"
                + "以下是从该用户以往对话中沉淀的历史偏好，用于个性化方案；"
                + "若与本轮用户的明确表述冲突，一律以本轮为准。\n";
        sb.append(header);
        int used = header.length();

        int stableLimit = Math.min(stable.size(), memoryProperties.getStableMaxItems());
        int ephemeralLimit = Math.min(ephemeral.size(), memoryProperties.getEphemeralMaxItems());

        StringBuilder stableBlock = new StringBuilder();
        int stableUsed = appendLines(stableBlock, stable.subList(0, stableLimit), used, budget);
        if (stableUsed > 0) {
            sb.append("■ 长期偏好\n").append(stableBlock);
            used += stableUsed + "■ 长期偏好\n".length();
        }

        int ephemeralUsed = 0;
        if (ephemeralLimit > 0) {
            StringBuilder ephemeralBlock = new StringBuilder();
            ephemeralUsed = appendLines(ephemeralBlock, ephemeral.subList(0, ephemeralLimit), used, budget);
            if (ephemeralUsed > 0) {
                sb.append("■ 时效信息\n").append(ephemeralBlock);
                used += ephemeralUsed + "■ 时效信息\n".length();
            }
        }

        // 两层都没写出内容（预算被头部占满）→ 视为无可用记忆，避免注入一段只有标题的空提示
        if (stableUsed == 0 && ephemeralUsed == 0) {
            log.debug("记忆注入预算不足，本轮不注入长期记忆");
            return null;
        }
        String text = sb.toString().trim();
        log.debug("长期记忆上下文构建完成，稳定层 {} 条，时效层 {} 条，共 {} 字",
                stableLimit, ephemeralLimit, text.length());
        return text;
    }

    /**
     * 渲染一组记忆行，受剩余预算约束
     *
     * @param sb     输出缓冲
     * @param hits   本组命中
     * @param used   已用字符数
     * @param budget 总预算
     * @return 实际写入的字符数
     */
    private int appendLines(StringBuilder sb, List<MemoryHit> hits, int used, int budget) {
        int written = 0;
        for (MemoryHit hit : hits) {
            String line = formatLine(hit);
            if (used + written + line.length() > budget) {
                // 预算用尽即停：宁可少注入一条，也不能让记忆把上下文挤爆
                break;
            }
            sb.append(line);
            written += line.length();
        }
        return written;
    }

    /**
     * 单行格式：类型 + 内容 + 置信度（时效信息额外带有效期）
     */
    private String formatLine(MemoryHit hit) {
        String typeLabel = hit.getType() == null ? "事实" : hit.getType().getLabel();
        StringBuilder line = new StringBuilder()
                .append("- [").append(typeLabel).append("] ")
                .append(hit.getContent() == null ? "" : hit.getContent())
                .append("（置信 ").append(String.format("%.2f", hit.getConfidence()));
        if (hit.getValidTo() != null) {
            line.append("，有效期至 ").append(hit.getValidTo().format(DATE_FORMATTER));
        }
        return line.append("）\n").toString();
    }
}
