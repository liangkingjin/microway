package com.wtlx.repository.mapper;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 记忆抽取台账数据访问（幂等控制）。
 * <p>
 * 抽取跑在异步线程池里、可能因异常被重试，也可能同一用户在多实例上并行触发；
 * 若没有台账，"用户说了一句话"可能被反复抽取并反复写入记忆，直接制造冗余。
 * 这里用 {@code (subject_key, conversation_id, turn_hash)} 唯一约束 + {@code ON CONFLICT DO NOTHING}
 * 把"同一轮只抽取一次"落到数据库层，而不是靠应用层的先查后写（那在并发下必然失效）。
 *
 * @author 金小梁
 */
@Slf4j
@Repository
public class MemoryExtractionMapper {

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /**
     * 抢占一轮抽取任务
     *
     * @return true = 本次抢到（首次处理该轮）；false = 已被处理过，直接跳过
     */
    public boolean tryClaimTurn(String subjectKey, String conversationId, String turnHash) {
        int rows = jdbcTemplate.update("""
                        INSERT INTO user_memory_extraction (subject_key, conversation_id, turn_hash, candidate_count, applied_count)
                        VALUES (?, ?, ?, 0, 0)
                        ON CONFLICT (subject_key, conversation_id, turn_hash) DO NOTHING
                        """,
                subjectKey, conversationId, turnHash);
        return rows > 0;
    }

    /**
     * 回填抽取结果（候选数 / 实际落库数），仅用于观测抽取质量
     */
    public void finishTurn(String subjectKey, String conversationId, String turnHash,
                           int candidateCount, int appliedCount) {
        try {
            jdbcTemplate.update("""
                            UPDATE user_memory_extraction SET candidate_count = ?, applied_count = ?
                             WHERE subject_key = ? AND conversation_id = ? AND turn_hash = ?
                            """,
                    candidateCount, appliedCount, subjectKey, conversationId, turnHash);
        } catch (Exception e) {
            log.warn("抽取台账回填失败: {}", e.getMessage());
        }
    }

    /**
     * 释放抢占（抽取过程中出现不可恢复错误时调用），使该轮可在下次重试。
     * 若模型持续失败则不应无限重试，故调用方需自行控制重试次数
     */
    public void releaseTurn(String subjectKey, String conversationId, String turnHash) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM user_memory_extraction WHERE subject_key = ? AND conversation_id = ? AND turn_hash = ?",
                    subjectKey, conversationId, turnHash);
        } catch (Exception e) {
            log.warn("抽取台账释放失败: {}", e.getMessage());
        }
    }

    /**
     * 某个会话已抽取的轮次数（观测用）
     */
    public int countByConversation(String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memory_extraction WHERE conversation_id = ?",
                Integer.class, conversationId);
        return count == null ? 0 : count;
    }
}
