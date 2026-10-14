package com.wtlx.infrastructure.memory.extraction;

import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.memory.MemoryCandidate;
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
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 记忆抽取调度服务：负责"什么时候抽、抽几次、抽完交给谁"。
 * <p>
 * 三个关键设计：
 * <ol>
 *     <li><b>全异步</b>——抽取要调 LLM，耗时以秒计。它挂在 SSE 流的收尾处，
 *         一旦同步执行就会让用户看到回答写完了、连接却迟迟不关。因此只在
 *         {@code memoryExtractionExecutor} 里跑，失败仅记日志。</li>
 *     <li><b>幂等</b>——先抢占台账（{@code INSERT ... ON CONFLICT DO NOTHING}）再干活，
 *         保证同一轮对话无论重试几次、在几个实例上被触发，都只抽取一次。</li>
 *     <li><b>成本前置过滤</b>——寒暄、纯提问、超短消息在调模型之前就被挡掉。
 *         记忆抽取是"每轮都可能付费"的后台任务，过滤器比提示词更能决定长期成本。</li>
 * </ol>
 * 抽取结果不直接落库，而是交给 {@link MemoryMergeService} 做合并与冲突校验——
 * 这是"记忆不被幻觉污染"的关键闸门。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class MemoryExtractionService {

    /**
     * 偏好信号词：出现即认为本轮大概率含个性化信息，值得花一次 LLM 调用。
     * <p>
     * 宁可漏抽也不滥抽：漏抽的信息会在后续对话里再次出现（用户偏好是稳定的），
     * 而滥抽产生的噪声记忆一旦落库，就要靠冲突校验与清理去收拾。
     * <p>
     * 用 {@link Set#of} 构建，<b>元素必须互不重复</b>——重复会让类初始化抛
     * {@code IllegalArgumentException}。词表增删后务必确认无重复项。
     */
    private static final Set<String> PREFERENCE_HINTS = Set.of(
            "我喜欢", "我不喜欢", "我讨厌", "我偏好", "我想去", "我打算", "我计划", "我准备",
            "预算", "忌口", "不吃", "吃辣", "素食", "清真", "过敏", "恐高", "晕车",
            "喜欢", "讨厌", "不想", "别给我", "不要", "避开", "受不了",
            "一个人", "独自", "和朋友", "和女朋友", "和男朋友", "带娃", "带孩子",
            "爸妈", "父母", "同事", "穷游", "深度游", "打卡", "休闲", "徒步",
            "摄影", "观星", "露营", "自驾", "高铁", "民宿", "青旅", "住宿",
            "拍照", "美食", "博物馆", "历史", "人文", "自然风光"
    );

    /**
     * 长消息阈值：超过该长度的发言即使用户没有用偏好句式，也大概率包含可沉淀信息
     */
    private static final int LONG_MESSAGE_CHARS = 20;

    /**
     * 会话摘要在提示词里的最大条数（只取本轮之前的历史，帮助模型解析指代）
     */
    private static final int DIGEST_MAX_MESSAGES = 4;

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private MemoryExtractionAgent memoryExtractionAgent;

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
     * 异步抽取并落库。
     * <p>
     * 调用点位于对话流收尾（assistant 回复已落库之后），因此本方法<b>必须立即返回</b>。
     *
     * @param subjectKey     记忆主体键；为 null 时静默跳过（未登录且无 clientId）
     * @param conversationId 会话 id，用于取短期上下文与台账幂等
     * @param userMessage    用户本轮发言
     * @param assistantReply 助手本轮回复
     */
    public void extractAsync(String subjectKey, String conversationId, String userMessage, String assistantReply) {
        if (!StringUtils.hasText(subjectKey) || !StringUtils.hasText(conversationId)) {
            return;
        }
        if (!memoryProperties.isEnabled() || !memoryProperties.isExtractionEnabled()) {
            return;
        }
        if (!shouldExtract(userMessage)) {
            log.debug("本轮不满足抽取条件，跳过长期记忆抽取, conversationId: {}", conversationId);
            return;
        }
        String turnHash = memoryNormalizer.contentHash(conversationId + "|" + userMessage);
        try {
            memoryExtractionExecutor.execute(() ->
                    doExtract(subjectKey, conversationId, turnHash, userMessage, assistantReply));
        } catch (Exception e) {
            // 线程池拒绝（极端情况，CallerRunsPolicy 之后仍失败）：不能影响对话收尾
            log.warn("记忆抽取任务提交失败，本轮不抽取: {}", e.getMessage());
        }
    }

    /**
     * 同步抽取（管理接口手动触发 / 补偿），会阻塞调用线程，仅用于调试与回填
     *
     * @return 实际落库（非 NOOP）的记忆条数
     */
    public int extractNow(String subjectKey, String conversationId, String userMessage, String assistantReply) {
        if (!StringUtils.hasText(subjectKey) || !StringUtils.hasText(conversationId)) {
            return 0;
        }
        String turnHash = memoryNormalizer.contentHash(conversationId + "|" + userMessage);
        return doExtract(subjectKey, conversationId, turnHash, userMessage, assistantReply);
    }

    /**
     * 抽取主体流程：抢占台账 → 取上下文 → 调子 Agent → 合并落库 → 回填台账
     *
     * @return 实际落库条数
     */
    private int doExtract(String subjectKey, String conversationId, String turnHash,
                          String userMessage, String assistantReply) {
        // 1. 幂等抢占：抢不到说明该轮已被处理过
        if (!memoryExtractionMapper.tryClaimTurn(subjectKey, conversationId, turnHash)) {
            log.debug("该轮对话已抽取过，跳过, conversationId: {}, turnHash: {}", conversationId, turnHash);
            return 0;
        }
        try {
            // 2. 拼接会话摘要：让模型能解析"那住宿呢"这类指代，避免抽出无主语候选
            String digest = buildSessionDigest(conversationId);

            // 3. 抽取候选
            List<MemoryCandidate> candidates = memoryExtractionAgent.extract(userMessage, assistantReply, digest);
            if (candidates.isEmpty()) {
                memoryExtractionMapper.finishTurn(subjectKey, conversationId, turnHash, 0, 0);
                return 0;
            }
            int limited = Math.min(candidates.size(), memoryProperties.getExtractionMaxCandidates());
            if (candidates.size() > limited) {
                candidates = candidates.subList(0, limited);
            }

            // 4. 合并 / 冲突校验后落库
            MemoryMergeService.MergeOutcome outcome = memoryMergeService.merge(subjectKey, conversationId, candidates);

            // 5. 回填台账，供观测抽取质量（候选数 / 落库数的比例）
            memoryExtractionMapper.finishTurn(subjectKey, conversationId, turnHash,
                    candidates.size(), outcome.appliedCount());
            log.info("长期记忆抽取完成, subject: {}, 候选 {} 条, 落库 {} 条, 决策分布 {}",
                    subjectKey, candidates.size(), outcome.appliedCount(), outcome.summary());
            return outcome.appliedCount();
        } catch (Exception e) {
            // 不可恢复错误：释放台账以便下次重试（重试上限由调用频率天然限制——
            // 同一轮对话不会再触发第二次，除非人工补偿）
            memoryExtractionMapper.releaseTurn(subjectKey, conversationId, turnHash);
            log.error("长期记忆抽取失败, subject: {}, conversationId: {}", subjectKey, conversationId, e);
            return 0;
        }
    }

    /**
     * 成本前置过滤：判断本轮是否值得抽取
     */
    private boolean shouldExtract(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.trim();
        if (text.length() < memoryProperties.getExtractionMinChars()) {
            return false;
        }
        for (String hint : PREFERENCE_HINTS) {
            if (text.contains(hint)) {
                return true;
            }
        }
        return text.length() >= LONG_MESSAGE_CHARS;
    }

    /**
     * 取本轮之前的历史消息拼成摘要串（最近的在前，帮助解析指代）。
     * 当前轮的"用户发言 + 助手回复"已落库，故从末尾裁掉两条
     */
    private String buildSessionDigest(String conversationId) {
        try {
            List<ConversationMessage> recent =
                    conversationMemoryService.loadRecentMessages(conversationId, DIGEST_MAX_MESSAGES + 2);
            if (recent.size() <= 2) {
                return null;
            }
            List<ConversationMessage> history = recent.subList(0, recent.size() - 2);
            StringBuilder sb = new StringBuilder();
            for (ConversationMessage message : history) {
                String content = message.getContent() == null ? "" : message.getContent();
                sb.append(message.getRole().getCode().equals("USER") ? "用户：" : "助手：")
                        .append(content.length() > 150 ? content.substring(0, 150) + "..." : content)
                        .append('\n');
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            log.debug("构建会话摘要失败，抽取时将不带上下文: {}", e.getMessage());
            return null;
        }
    }
}
