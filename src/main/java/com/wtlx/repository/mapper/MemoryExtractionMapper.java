package com.wtlx.repository.mapper;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 记忆抽取台账数据访问（段级抽取观测）。
 * <p>
 * 段级抽取后，用 {@code (subject_key, conversation_id, turn_hash)} 唯一约束 +
 * {@code ON CONFLICT DO NOTHING} 记录"某段记录已被抽取过"，既便于观测抽取质量
 * （候选数 / 落库数），又能在重试时避免同一段重复记行。这里的 {@code turn_hash} 列
 * 复用作"段指纹"。真正的去重由合并闸门的 {@code dedup_key} 唯一索引负责，
 * 本表不承担幂等写入的职责。
 *
 * @author 金小梁
 */
@Slf4j
@Repository
public class MemoryExtractionMapper {

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /**
     * 记录一段会话记录的抽取结果（候选数 / 实际落库数），仅用于观测抽取质量。
     * 同一段指纹已存在时忽略，避免重试重复记行。
     */
    public void recordSegment(String subjectKey, String conversationId, String segmentHash,
                             int candidateCount, int appliedCount) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO user_memory_extraction (subject_key, conversation_id, turn_hash, candidate_count, applied_count)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT (subject_key, conversation_id, turn_hash) DO NOTHING
                            """,
                    subjectKey, conversationId, segmentHash, candidateCount, appliedCount);
        } catch (Exception e) {
            log.warn("记忆段抽取台账写入失败: {}", e.getMessage());
        }
    }
}
