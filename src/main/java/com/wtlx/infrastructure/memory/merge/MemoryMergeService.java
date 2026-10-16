package com.wtlx.infrastructure.memory.merge;

import com.google.common.util.concurrent.Striped;
import com.wtlx.domain.entity.memory.MemoryCandidate;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.domain.entity.memory.UserMemoryEvent;
import com.wtlx.domain.entity.memory.enums.MemoryDecision;
import com.wtlx.domain.entity.memory.enums.MemoryStatus;
import com.wtlx.infrastructure.memory.MemoryLlmAgent;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.embedding.MemoryEmbeddingService;
import com.wtlx.infrastructure.memory.support.MemoryConstants;
import com.wtlx.infrastructure.memory.support.MemoryNormalizer;
import com.wtlx.repository.mapper.UserMemoryEventMapper;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

/**
 * 记忆合并与冲突校验服务——长期记忆的"写入闸门"。
 * <p>
 * <b>为什么不能直接写库</b>：抽取子 Agent 只看得到本轮对话，看不见用户的历史画像。
 * 若把候选直接插入，同一件事会因为表达差异累积成十几条互相矛盾的记忆
 * （"喜欢安静" / "不去人多的地方" / "能接受热闹"），检索时无从取舍，模型也会被相互矛盾的历史带偏。
 * 因此每条候选都必须先与已有记忆比对，得出 ADD / UPDATE / SUPERSEDE / FORGET / NOOP 之一后才落库。
 * <p>
 * <b>三级判定，先便宜后昂贵</b>：
 * <ol>
 *     <li><b>指纹级</b>——{@code dedup_key} 精确命中即重复，只巩固置信度，零 LLM 成本；</li>
 *     <li><b>规则级</b>——相似度 ≥ 重复线判重复、&lt; 冲突线判新增，两边都是明确结论；</li>
 *     <li><b>模型级</b>——只有"同槽位取值不同"或"相似度落在灰区"才调 LLM 裁决。
 *         实际调用比例远低于抽取次数。</li>
 * </ol>
 * 并发安全：抽取是异步的，同一个用户可能被多轮任务并行处理，因此按主体键加分段锁串行化
 * "比对 + 决策 + 落库"。跨实例并发由 {@code uk_user_memory_active_dedup} 部分唯一索引兜底
 * （捕获唯一约束冲突转 NOOP）。<b>已知限制</b>：分段锁只在单实例内有效，多实例部署时
 * 可能出现两条语义冲突的记忆同时生效，需靠清理任务收敛——这是刻意的取舍，
 * 引入分布式锁会把每轮对话都串到 Redis 上，代价高于收益。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class MemoryMergeService {

    /**
     * 分段锁：按主体键哈希取锁，锁粒度细到"每个用户一把"，不阻塞其他用户
     */
    private final Striped<Lock> subjectLocks = Striped.lock(64);

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private MemoryNormalizer memoryNormalizer;

    @Resource
    private MemoryEmbeddingService memoryEmbeddingService;

    @Resource
    private MemoryLlmAgent memoryLlmAgent;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private UserMemoryEventMapper userMemoryEventMapper;

    /**
     * 合并结果统计
     *
     * @param counts 各决策的条数
     */
    public record MergeOutcome(Map<MemoryDecision, Integer> counts) {

        public static MergeOutcome empty() {
            return new MergeOutcome(new EnumMap<>(MemoryDecision.class));
        }

        /**
         * 实际产生变更的条数（NOOP 只是巩固，不计入）
         */
        public int appliedCount() {
            int total = 0;
            for (Map.Entry<MemoryDecision, Integer> entry : counts.entrySet()) {
                if (entry.getKey() != MemoryDecision.NOOP) {
                    total += entry.getValue() == null ? 0 : entry.getValue();
                }
            }
            return total;
        }

        /**
         * 便于日志观察的紧凑分布串
         */
        public String summary() {
            if (counts.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            counts.forEach((decision, count) -> {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(decision.getCode()).append('=').append(count);
            });
            return sb.append('}').toString();
        }
    }

    /**
     * 合并一批候选并落库
     *
     * @param subjectKey 记忆主体键
     * @param sourceRef  来源会话 id（写入证据链）
     * @param candidates 抽取出的候选
     * @return 合并结果统计
     */
    public MergeOutcome merge(String subjectKey, String sourceRef, List<MemoryCandidate> candidates) {
        if (!StringUtils.hasText(subjectKey) || candidates == null || candidates.isEmpty()) {
            return MergeOutcome.empty();
        }
        Lock lock = subjectLocks.get(subjectKey);
        lock.lock();
        try {
            Map<MemoryDecision, Integer> counts = new EnumMap<>(MemoryDecision.class);
            for (MemoryCandidate candidate : candidates) {
                try {
                    applyOne(subjectKey, sourceRef, candidate, counts);
                } catch (Exception e) {
                    // 单条候选失败不影响整批：一批里通常有若干独立信息，不该因一条格式异常全丢
                    log.warn("记忆候选合并失败，跳过该条: content={}, 原因={}",
                            abbreviate(candidate.getContent()), e.getMessage());
                }
            }
            MergeOutcome outcome = new MergeOutcome(counts);
            return outcome;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 单条候选的完整判定与落库流程
     */
    private void applyOne(String subjectKey, String sourceRef, MemoryCandidate candidate,
                          Map<MemoryDecision, Integer> counts) {
        // 1. 已过有效期的候选直接丢弃（模型有时会抽出已经发生过的行程）
        if (candidate.getValidTo() != null && candidate.getValidTo().isBefore(LocalDateTime.now())) {
            log.debug("候选已过有效期，丢弃: {}", abbreviate(candidate.getContent()));
            return;
        }

        String key = memoryNormalizer.normalizeKey(candidate.getKey());
        String content = candidate.getContent().trim();
        String dedupKey = memoryNormalizer.dedupKey(candidate.getType(), key, content);
        String contentHash = memoryNormalizer.contentHash(content);
        String literal = memoryEmbeddingService.embedToLiteral(content);

        // 2. 指纹级去重：完全相同的槽位 + 取值 → 只巩固
        UserMemory exact = userMemoryMapper.findActiveByDedupKey(subjectKey, dedupKey);
        if (exact != null) {
            userMemoryMapper.confirm(exact.getId(), candidate.getConfidence(), candidate.getEvidence());
            record(counts, MemoryDecision.NOOP);
            logEvent(subjectKey, sourceRef, MemoryDecision.NOOP, candidate, key,
                    exact.getContent(), exact.getContent(),
                    "去重键一致，判定为重复信息，仅巩固置信度", exact.getMemoryId());
            return;
        }

        // 3. 收集相关记忆：同槽位（语义冲突与相似度无关）+ 向量近邻
        List<UserMemory> sameSlot = userMemoryMapper.findActiveByKey(subjectKey, key, 3);
        List<ScoredMemory> nearBy = literal == null
                ? List.of()
                : userMemoryMapper.findSimilarActive(subjectKey, literal, memoryProperties.getSemanticSearchTopK());
        List<ScoredMemory> related = unionRelated(sameSlot, nearBy);
        double bestSimilarity = nearBy.isEmpty() ? 0D : nearBy.get(0).pathScore();

        // 4. 决策（复用上面已算好的 content，避免再次 trim）
        Decision decision = decide(candidate, content, sameSlot, nearBy, related, bestSimilarity);

        // 5. 落地
        apply(subjectKey, sourceRef, candidate, key, content, contentHash, dedupKey, literal, decision);
        record(counts, decision.decision());
    }

    /**
     * 决策结果载体
     *
     * @param decision 决策类型
     * @param target   针对的已有记忆（ADD 时为 null）
     * @param reason   决策理由
     * @param content  最终要写入的内容（UPDATE 时可能是模型合并后的表述）
     */
    private record Decision(MemoryDecision decision, UserMemory target, String reason, String content) {
    }

    /**
     * 三级判定：规则 → 灰区交模型 → 兜底保守新增
     *
     * @param content 已 trim 的候选内容（调用方算好传入，避免本方法再次 trim）
     */
    private Decision decide(MemoryCandidate candidate, String content, List<UserMemory> sameSlot,
                            List<ScoredMemory> nearBy, List<ScoredMemory> related, double bestSimilarity) {
        // 分支一：存在同槽位记忆 —— 槽位相同即"同一件事"的强信号，交模型裁决
        if (!sameSlot.isEmpty()) {
            MemoryLlmAgent.Verdict verdict = memoryLlmAgent.judge(candidate, related);
            if (verdict != null) {
                return fromVerdict(verdict, related, content);
            }
            // 模型不可用：按"最新表述为准"保守取代，符合用户改口比坚持旧设定更常见的现实
            UserMemory target = sameSlot.get(0);
            return new Decision(MemoryDecision.SUPERSEDE, target,
                    "同槽位不同取值且判定模型不可用，按最新表述取代旧记忆（规则兜底）", content);
        }

        // 分支二：无同槽位，但语义高度相似 —— 同一事实的不同表述
        if (bestSimilarity >= memoryProperties.getDuplicateSimilarity() && !nearBy.isEmpty()) {
            UserMemory nearest = nearBy.get(0).memory();
            if (content.length() > nearest.getContent().length()) {
                // 新表述更完整 → 就地丰富，避免"越说越短"把细节丢掉
                return new Decision(MemoryDecision.UPDATE, nearest,
                        "语义重复但新表述更完整，就地丰富", content);
            }
            return new Decision(MemoryDecision.NOOP, nearest, "语义重复，仅巩固置信度", content);
        }

        // 分支三：语义灰区（相似但不到重复线）—— 可能相关，可能只是同域话题，交模型判断
        if (bestSimilarity >= memoryProperties.getConflictSimilarity()) {
            MemoryLlmAgent.Verdict verdict = memoryLlmAgent.judge(candidate, related);
            if (verdict != null) {
                return fromVerdict(verdict, related, content);
            }
            return new Decision(MemoryDecision.ADD, null, "相似但无法判定冲突，保守新增", content);
        }

        // 分支四：与已有记忆无明显关联
        return new Decision(MemoryDecision.ADD, null, "与已有记忆无明显关联，直接新增", content);
    }

    /**
     * 把模型判定映射为内部决策（含目标记忆解析）
     */
    private Decision fromVerdict(MemoryLlmAgent.Verdict verdict, List<ScoredMemory> related, String content) {
        MemoryDecision decision = verdict.decision();
        UserMemory target = null;
        if (verdict.targetIndex() >= 0 && verdict.targetIndex() < related.size()) {
            target = related.get(verdict.targetIndex()).memory();
        }
        String finalContent = StringUtils.hasText(verdict.mergedContent()) ? verdict.mergedContent().trim() : content;

        // 除了 ADD 以外都必须有目标；模型偶发给了决策却没给目标时退化为新增，宁可多一条也不误覆盖
        if (decision != MemoryDecision.ADD && target == null) {
            return new Decision(MemoryDecision.ADD, null, "判定缺失有效目标，退化为新增", content);
        }
        return new Decision(decision, target, verdict.reason(), finalContent);
    }

    /**
     * 按决策落地写库
     */
    private void apply(String subjectKey, String sourceRef, MemoryCandidate candidate, String key,
                       String content, String contentHash, String dedupKey, String literal, Decision decision) {
        switch (decision.decision()) {
            case ADD -> insertNew(subjectKey, sourceRef, candidate, key, content, contentHash, dedupKey, literal,
                    decision.reason());

            case UPDATE -> {
                UserMemory target = decision.target();
                int nextVersion = target.getVersion() + 1;
                String structured = candidate.getStructured() != null ? candidate.getStructured() : target.getStructured();
                userMemoryMapper.updateContent(target.getId(), decision.content(), structured,
                        Math.max(target.getConfidence(), candidate.getConfidence()),
                        Math.max(target.getImportance(), candidate.getImportance()),
                        nextVersion, candidate.getEvidence());
                logEvent(subjectKey, sourceRef, MemoryDecision.UPDATE, candidate, key,
                        target.getContent(), decision.content(), decision.reason(), target.getMemoryId());
            }

            case SUPERSEDE -> {
                UserMemory target = decision.target();
                String newMemoryId = insertNew(subjectKey, sourceRef, candidate, key, content, contentHash,
                        dedupKey, literal, decision.reason());
                if (newMemoryId != null) {
                    userMemoryMapper.supersede(target.getId(), newMemoryId);
                    logEvent(subjectKey, sourceRef, MemoryDecision.SUPERSEDE, candidate, key,
                            target.getContent(), content, decision.reason(), newMemoryId);
                }
            }

            case FORGET -> {
                UserMemory target = decision.target();
                userMemoryMapper.expire(target.getId());
                logEvent(subjectKey, sourceRef, MemoryDecision.FORGET, candidate, key,
                        target.getContent(), null, decision.reason(), target.getMemoryId());
            }

            case NOOP -> {
                UserMemory target = decision.target();
                if (target != null) {
                    userMemoryMapper.confirm(target.getId(), candidate.getConfidence(), candidate.getEvidence());
                }
                logEvent(subjectKey, sourceRef, MemoryDecision.NOOP, candidate, key,
                        target == null ? null : target.getContent(),
                        target == null ? null : target.getContent(), decision.reason(),
                        target == null ? null : target.getMemoryId());
            }

            // CLEANUP 只由清理任务产生，永远不会走到抽取合并流程；显式列出而非 default，
            // 这样将来新增决策枚举项时编译器会立刻报错，不会被静默忽略
            case CLEANUP -> log.warn("批量清理决策不应出现在抽取合并流程，忽略: {}", decision.decision());
        }
    }

    /**
     * 插入新记忆（状态 ACTIVE），返回其对外标识；唯一约束冲突时视为重复并返回 null
     */
    private String insertNew(String subjectKey, String sourceRef, MemoryCandidate candidate, String key,
                             String content, String contentHash, String dedupKey, String literal, String reason) {
        String memoryId = UUID.randomUUID().toString();
        UserMemory memory = new UserMemory();
        memory.setMemoryId(memoryId);
        memory.setSubjectKey(subjectKey);
        memory.setUserId(extractUserId(subjectKey));
        memory.setMemoryType(candidate.getType().getCode());
        memory.setMemoryKey(key);
        memory.setContent(content);
        memory.setStructured(candidate.getStructured());
        memory.setConfidence(candidate.getConfidence());
        memory.setImportance(candidate.getImportance());
        memory.setStatus(MemoryStatus.ACTIVE.getCode());
        memory.setSourceType(MemoryConstants.SOURCE_CONVERSATION);
        memory.setSourceRef(sourceRef);
        memory.setEvidence(candidate.getEvidence());
        memory.setValidFrom(LocalDateTime.now());
        memory.setValidTo(candidate.getValidTo());
        memory.setVersion(1);
        memory.setContentHash(contentHash);
        memory.setDedupKey(dedupKey);
        memory.setEmbeddingLiteral(literal);
        try {
            userMemoryMapper.insert(memory);
            logEvent(subjectKey, sourceRef, MemoryDecision.ADD, candidate, key, null, content, reason, memoryId);
            return memoryId;
        } catch (DataIntegrityViolationException e) {
            // 跨实例并发下另一个线程刚写入了同一条记忆：唯一索引拦住了，等价于 NOOP
            log.debug("记忆唯一约束冲突，判定为并发重复写入: {}", abbreviate(content));
            return null;
        }
    }

    /**
     * 合并"同槽位"与"向量近邻"两组相关记忆，保持同槽位在前（判定目标优先指向同槽位记忆）
     */
    private List<ScoredMemory> unionRelated(List<UserMemory> sameSlot, List<ScoredMemory> nearBy) {
        Map<String, ScoredMemory> merged = new LinkedHashMap<>();
        for (UserMemory memory : sameSlot) {
            merged.put(memory.getMemoryId(), new ScoredMemory(memory, 0D));
        }
        for (ScoredMemory scored : nearBy) {
            merged.putIfAbsent(scored.memory().getMemoryId(), scored);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 从主体键解析 userId 落库（便于业务侧按用户 id 直查，不必再解析前缀）
     */
    private String extractUserId(String subjectKey) {
        if (subjectKey.startsWith(MemoryConstants.SUBJECT_USER_PREFIX)) {
            String userId = subjectKey.substring(MemoryConstants.SUBJECT_USER_PREFIX.length());
            return userId.length() > 64 ? userId.substring(0, 64) : userId;
        }
        return null;
    }

    private void record(Map<MemoryDecision, Integer> counts, MemoryDecision decision) {
        counts.merge(decision, 1, Integer::sum);
    }

    /**
     * 追加审计事件（失败不影响主流程）。
     *
     * @param key 已归一化的槽位键，由调用方传入——此前本方法自己再归一化一次，
     *            与 {@link #applyOne} 里的归一化重复计算，且两次归一化的入参口径一旦
     *            被改动就可能不一致
     */
    private void logEvent(String subjectKey, String sourceRef, MemoryDecision decision, MemoryCandidate candidate,
                          String key, String before, String after, String reason, String memoryId) {
        UserMemoryEvent event = new UserMemoryEvent();
        event.setMemoryId(memoryId);
        event.setSubjectKey(subjectKey);
        event.setDecision(decision.getCode());
        event.setMemoryType(candidate.getType().getCode());
        event.setMemoryKey(key);
        event.setBeforeContent(truncate(before, 500));
        event.setAfterContent(truncate(after, 500));
        event.setReason(truncate(reason, 500));
        event.setSourceRef(sourceRef);
        event.setCreatedAt(LocalDateTime.now());
        userMemoryEventMapper.insert(event);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 60 ? value.substring(0, 60) + "..." : value;
    }
}
