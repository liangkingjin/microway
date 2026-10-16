package com.wtlx.infrastructure.memory.extraction;

import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.memory.MemoryCandidate;
import com.wtlx.infrastructure.memory.MemoryLlmAgent;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.merge.MemoryMergeService;
import com.wtlx.infrastructure.memory.support.MemoryNormalizer;
import com.wtlx.repository.mapper.MemoryExtractionMapper;
import com.wtlx.service.ConversationMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记忆抽取调度服务：负责"什么时候抽、抽哪段、抽完交给谁"。
 * <p>
 * <b>简化后的写入链路</b>（相对此前每轮都抽、且带启发式前置过滤的版本）：
 * <ol>
 *     <li><b>段级触发</b>——每累计 {@code extraction-every-rounds}（默认 10）轮对话，
 *         才触发一次"该段会话记录"的提取，而不是每轮都抽。触发器是内存计数器，
 *         达到阈值后重置并异步抽取最近 {@code segment-max-rounds} 轮；</li>
 *     <li><b>提取完全基于 LLM</b>——前置的"53 个偏好信号词 + 长度阈值"过滤已移除，
 *         改由抽取 Agent 综合整段对话自行判断是否需要抽取；无需提取时直接返回空，
 *         后续合并自然跳过；</li>
 *     <li><b>冲突校验后置</b>——提取出的候选交给 {@link MemoryMergeService} 做冲突校验，
 *         检测并标记（必要时消解）新记录与已有数据间的冲突，只有过闸的才落库。</li>
 * </ol>
 * <p>
 * 与先前版本一致的两个设计保留：<b>全异步</b>（抽取挂在对话收尾，不挡 SSE）与
 * <b>合并闸门隔离"理解"与"裁决"</b>（子 Agent 只产出候选、不写库）。
 * <p>
 * <b>已知限制</b>：段触发器是内存计数器，单实例内准确；应用重启会丢失计数（最坏是某段被多抽一次，
 * 合并闸门的去重会吸收重复），多实例部署下各实例各自计数、可能各抽各的段——这与本项目
 * "单实例、不引入分布式锁"的取舍一致（见 {@link MemoryMergeService}）。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class MemoryExtractionService {

    /**
     * 每个会话累计的"未触发轮数"；达到阈值即触发一段抽取，然后清零
     */
    private final ConcurrentHashMap<String, AtomicInteger> pendingRounds = new ConcurrentHashMap<>();

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private MemoryLlmAgent memoryLlmAgent;

    @Resource
    private MemoryMergeService memoryMergeService;

    @Resource
    private MemoryExtractionMapper memoryExtractionMapper;

    @Resource
    private MemoryNormalizer memoryNormalizer;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource(name = "memoryExtractionExecutor")
    private Executor memoryExtractionExecutor;

    /**
     * 每轮对话收尾时调用：累计轮数，达到阈值才异步抽取该段会话记录。
     * <p>
     * 入参只需身份与会话——要抽取的"段"直接从会话库里取最近若干轮，
     * 不再依赖调用方逐轮透传 user/assistant 文本。
     *
     * @param subjectKey     记忆主体键；为 null 时静默跳过（未登录且无 clientId）
     * @param conversationId 会话 id，用于取该段记录与冲突校验的作用域
     */
    public void extractAsync(String subjectKey, String conversationId) {
        if (!StringUtils.hasText(subjectKey) || !StringUtils.hasText(conversationId)) {
            return;
        }
        if (!memoryProperties.isEnabled() || !memoryProperties.isExtractionEnabled()) {
            return;
        }
        AtomicInteger counter = pendingRounds.computeIfAbsent(conversationId, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() < memoryProperties.getExtractionEveryRounds()) {
            return; // 未累计满阈值，本轮不抽
        }
        counter.set(0); // 触发后立即清零，准备下一段
        try {
            memoryExtractionExecutor.execute(() -> extractSegmentNow(subjectKey, conversationId));
        } catch (Exception e) {
            // 线程池拒绝（极端情况）：不能影响对话收尾
            log.warn("记忆段抽取任务提交失败，本段不抽取: {}", e.getMessage());
        }
    }

    /**
     * 手动触发（管理接口 / 调试 / 补偿）：同步抽取当前会话最近一段，阻塞调用线程
     *
     * @return 实际落库（非 NOOP）的记忆条数
     */
    public int extractNow(String subjectKey, String conversationId) {
        if (!StringUtils.hasText(subjectKey) || !StringUtils.hasText(conversationId)) {
            return 0;
        }
        return extractSegmentNow(subjectKey, conversationId);
    }

    /**
     * 抽取一段会话记录并做冲突校验后落库
     *
     * @return 实际落库条数
     */
    private int extractSegmentNow(String subjectKey, String conversationId) {
        try {
            int messageLimit = memoryProperties.getSegmentMaxRounds() * 2;
            List<ConversationMessage> segment = conversationMemoryService.loadRecentMessages(conversationId, messageLimit);
            if (segment.isEmpty()) {
                return 0;
            }

            // 提取完全基于 LLM：是否值得抽取由模型判断，无需提取时返回空
            List<MemoryCandidate> candidates = memoryLlmAgent.extractSegment(segment);
            if (candidates.isEmpty()) {
                memoryExtractionMapper.recordSegment(subjectKey, conversationId,
                        segmentHash(conversationId, segment), 0, 0);
                return 0;
            }

            // 候选数上限：模型偶尔一次刷出过多低价值候选，截断比逐条校验更省事
            int maxCandidates = memoryProperties.getExtractionMaxCandidates();
            List<MemoryCandidate> limited = candidates.size() > maxCandidates
                    ? candidates.subList(0, maxCandidates)
                    : candidates;

            // 冲突校验（检测并标记 / 必要时消解新记录与已有数据间的冲突）后落库
            MemoryMergeService.MergeOutcome outcome =
                    memoryMergeService.merge(subjectKey, conversationId, limited);

            memoryExtractionMapper.recordSegment(subjectKey, conversationId,
                    segmentHash(conversationId, segment), limited.size(), outcome.appliedCount());
            log.info("长期记忆段抽取完成, subject: {}, 候选 {} 条, 落库 {} 条, 决策分布 {}",
                    subjectKey, limited.size(), outcome.appliedCount(), outcome.summary());
            return outcome.appliedCount();
        } catch (Exception e) {
            log.error("长期记忆段抽取失败, subject: {}, conversationId: {}", subjectKey, conversationId, e);
            return 0;
        }
    }

    /**
     * 段指纹：以会话 id + 段内消息 id 序列算出，保证同一段记录只在台账里记一行
     */
    private String segmentHash(String conversationId, List<ConversationMessage> segment) {
        StringBuilder ids = new StringBuilder(conversationId);
        for (ConversationMessage message : segment) {
            if (message != null && message.getId() != null) {
                ids.append('#').append(message.getId());
            }
        }
        return memoryNormalizer.contentHash(ids.toString());
    }
}
