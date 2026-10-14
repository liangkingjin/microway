package com.wtlx.infrastructure.memory.cleanup;

import com.wtlx.domain.entity.memory.DuplicatePair;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.domain.entity.memory.UserMemoryEvent;
import com.wtlx.domain.entity.memory.enums.MemoryDecision;
import com.wtlx.domain.entity.memory.enums.MemoryStatus;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.repository.mapper.UserMemoryEventMapper;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 长期记忆清理服务：按需回收冗余与过时记忆。
 * <p>
 * <b>为什么必须有清理</b>：记忆是只增不减的资产，没有回收机制的系统会在几个月后必然劣化——
 * 冗余会让检索结果被同义记忆刷屏（真正重要的偏好被挤出 topN），
 * 过时信息（"下个月去成都"）会让模型做出基于过期前提的规划，
 * 低置信噪声会持续稀释上下文质量，而无限增长还会拖慢 HNSW 扫描。
 * <p>
 * 五级清理，按"代价从低到高 / 风险从低到高"排序：
 * <ol>
 *     <li><b>过期回收</b>——{@code valid_to} 已到，纯 SQL，无歧义；</li>
 *     <li><b>低置信淘汰</b>——低置信 + 超过冷置期 + <b>零命中</b>（三重条件缺一不可，
 *         否则会误杀刚写入还没机会被命中的新记忆）；</li>
 *     <li><b>容量收敛</b>——单主体超过上限时按 {@code 置信度 × 重要度} 淘汰尾部；
 *         按主体逐个处理，避免一次扫描全表；</li>
 *     <li><b>冗余合并</b>——向量相似度找近重复对，保留信息更完整的一条，
 *         另一条置 SUPERSEDED 并指向保留方（不是删除，保留可追溯性）；</li>
 *     <li><b>物理清除</b>——非生效状态超过保留期后真正 DELETE。
 *         软删是为了审计，但审计价值有时效，90 天前的历史行既占空间又拖慢部分索引。</li>
 * </ol>
 * 全程只做状态流转，不做物理删除（除最后一步），因此清理是可解释、可回滚语义的：
 * {@code user_memory_event} 会记录每一次失效的原因。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class MemoryCleanupService {

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private UserMemoryEventMapper userMemoryEventMapper;

    /**
     * 清理统计
     *
     * @param expiredByValidTo  因有效期到点而失效
     * @param expiredLowConf    因低置信且长期零命中而失效
     * @param expiredOverCap    因超出主体容量上限而失效
     * @param mergedRedundant   因冗余合并被取代
     * @param purged            物理删除的历史行
     * @param subjectsScanned   扫描到的主体数
     */
    public record CleanupStat(int expiredByValidTo, int expiredLowConf, int expiredOverCap,
                              int mergedRedundant, int purged, int subjectsScanned) {

        public int totalExpired() {
            return expiredByValidTo + expiredLowConf + expiredOverCap + mergedRedundant;
        }

        public String summary() {
            return String.format("清理完成：过期 %d，低置信 %d，超容量 %d，冗余合并 %d，物理清除 %d，扫描主体 %d",
                    expiredByValidTo, expiredLowConf, expiredOverCap, mergedRedundant, purged, subjectsScanned);
        }

        static CleanupStat empty() {
            return new CleanupStat(0, 0, 0, 0, 0, 0);
        }

        CleanupStat plus(CleanupStat other) {
            return new CleanupStat(
                    expiredByValidTo + other.expiredByValidTo,
                    expiredLowConf + other.expiredLowConf,
                    expiredOverCap + other.expiredOverCap,
                    mergedRedundant + other.mergedRedundant,
                    purged + other.purged,
                    subjectsScanned + other.subjectsScanned);
        }
    }

    /**
     * 全库清理（定时任务入口）。
     * <p>
     * 过期与低置信是全局条件，一次 SQL 覆盖全库；容量与冗余必须按主体处理
     * （窗口函数与自连接都以主体为边界），因此遍历主体但限制批量。
     */
    public CleanupStat cleanupAll() {
        if (!memoryProperties.isEnabled()) {
            log.info("长期记忆未启用，跳过清理");
            return CleanupStat.empty();
        }
        long start = System.currentTimeMillis();
        int expiredByValidTo = userMemoryMapper.expireByValidTo(null);
        int expiredLowConf = userMemoryMapper.expireLowConfidence(
                memoryProperties.getLowConfidenceThreshold(), memoryProperties.getLowConfidenceStaleDays(), null);

        int overCap = 0;
        int merged = 0;
        // 用 Set 去重：超容量主体与冗余合并主体可能重叠，避免重复扫描计数
        Set<String> subjects = new LinkedHashSet<>(
                userMemoryMapper.listActiveSubjects(memoryProperties.getCleanupBatchSubjects()));
        for (String subjectKey : subjects) {
            overCap += enforceCap(subjectKey);
            merged += mergeRedundant(subjectKey);
        }

        int purged = userMemoryMapper.purgeSoftDeleted(memoryProperties.getPurgeRetentionDays());
        CleanupStat stat = new CleanupStat(expiredByValidTo, expiredLowConf, overCap, merged, purged, subjects.size());
        log.info("{}，耗时 {}ms", stat.summary(), System.currentTimeMillis() - start);
        return stat;
    }

    /**
     * 单主体清理（按需清理入口：用户觉得"记错了"/运维针对单个用户收敛）
     */
    public CleanupStat cleanupSubject(String subjectKey) {
        if (!StringUtils.hasText(subjectKey)) {
            return CleanupStat.empty();
        }
        int expiredByValidTo = userMemoryMapper.expireByValidTo(subjectKey);
        int expiredLowConf = userMemoryMapper.expireLowConfidence(
                memoryProperties.getLowConfidenceThreshold(), memoryProperties.getLowConfidenceStaleDays(), subjectKey);
        int overCap = enforceCap(subjectKey);
        int merged = mergeRedundant(subjectKey);
        CleanupStat stat = new CleanupStat(expiredByValidTo, expiredLowConf, overCap, merged, 0, 1);
        if (stat.totalExpired() > 0) {
            logEvent(subjectKey, stat);
        }
        log.info("单主体记忆清理完成, subject: {}, {}", subjectKey, stat.summary());
        return stat;
    }

    /**
     * 容量收敛：超出上限时按 置信度×重要度 淘汰尾部
     */
    private int enforceCap(String subjectKey) {
        int cap = memoryProperties.getActiveCapPerSubject();
        if (userMemoryMapper.countActive(subjectKey) <= cap) {
            return 0;
        }
        return userMemoryMapper.expireOverCap(subjectKey, cap);
    }

    /**
     * 冗余合并：近重复记忆对按相似度降序贪心收敛。
     * <p>
     * 每处理一对都实时复核双方的 ACTIVE 状态——若保留方自身已在前面某对被判为冗余而被取代，
     * 这一对整体跳过，否则会出现"A 保留、B 保留、B 又被取代"的自相矛盾状态。
     *
     * @return 被取代的条数
     */
    private int mergeRedundant(String subjectKey) {
        int merged = 0;
        try {
            // 先收敛内容完全一致的残留（唯一索引上线前的脏数据修复）
            merged += userMemoryMapper.expireExactDuplicateBySlot(subjectKey);

            List<DuplicatePair> pairs = userMemoryMapper.findDuplicatePairs(
                    subjectKey, memoryProperties.getDuplicateSimilarity(), 100);
            for (DuplicatePair pair : pairs) {
                UserMemory keep = userMemoryMapper.findByMemoryId(subjectKey, pair.keepMemoryId());
                UserMemory drop = userMemoryMapper.findByMemoryId(subjectKey, pair.dropMemoryId());
                if (keep == null || drop == null) {
                    continue;
                }
                if (!MemoryStatus.ACTIVE.getCode().equals(keep.getStatus())
                        || !MemoryStatus.ACTIVE.getCode().equals(drop.getStatus())) {
                    continue;
                }
                if (userMemoryMapper.supersede(drop.getId(), keep.getMemoryId()) > 0) {
                    merged++;
                    logEvent(subjectKey, MemoryDecision.CLEANUP, drop.getMemoryId(), keep.getMemoryId(),
                            String.format("冗余合并：与记忆 %s 相似度 %.3f，判定为同一事实的不同表述，保留更完整的一条",
                                    keep.getMemoryId(), pair.similarity()));
                }
            }
        } catch (Exception e) {
            // 冗余合并是"锦上添花"的一步，失败不应让整轮清理失败
            log.warn("冗余合并失败, subject: {}, 原因: {}", subjectKey, e.getMessage());
        }
        return merged;
    }

    /**
     * 记录一次清理事件（聚合粒度）
     */
    private void logEvent(String subjectKey, CleanupStat stat) {
        UserMemoryEvent event = new UserMemoryEvent();
        event.setSubjectKey(subjectKey);
        event.setDecision(MemoryDecision.CLEANUP.getCode());
        event.setReason("批量清理：" + stat.summary());
        event.setCreatedAt(LocalDateTime.now());
        userMemoryEventMapper.insert(event);
    }

    /**
     * 记录单条冗余合并事件
     */
    private void logEvent(String subjectKey, MemoryDecision decision, String memoryId, String keepMemoryId,
                          String reason) {
        UserMemoryEvent event = new UserMemoryEvent();
        event.setSubjectKey(subjectKey);
        event.setDecision(decision.getCode());
        event.setMemoryId(memoryId);
        event.setBeforeContent("被取代方：" + memoryId);
        event.setAfterContent("保留方：" + keepMemoryId);
        event.setReason(reason);
        event.setCreatedAt(LocalDateTime.now());
        userMemoryEventMapper.insert(event);
    }
}
