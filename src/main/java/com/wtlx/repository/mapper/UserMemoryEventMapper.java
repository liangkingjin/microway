package com.wtlx.repository.mapper;

import com.wtlx.domain.entity.memory.UserMemoryEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆变更事件数据访问（审计流）。
 * <p>
 * 写入是"尽力而为"：审计失败绝不能让记忆写入回滚——记忆本身的正确性优先于可追溯性，
 * 因此所有写方法都不抛异常，只记日志。
 *
 * @author 金小梁
 */
@Slf4j
@Repository
public class UserMemoryEventMapper {

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<UserMemoryEvent> rowMapper = (rs, rowNum) -> {
        UserMemoryEvent event = new UserMemoryEvent();
        event.setId(rs.getLong("id"));
        event.setMemoryId(rs.getString("memory_id"));
        event.setSubjectKey(rs.getString("subject_key"));
        event.setDecision(rs.getString("decision"));
        event.setMemoryType(rs.getString("memory_type"));
        event.setMemoryKey(rs.getString("memory_key"));
        event.setBeforeContent(rs.getString("before_content"));
        event.setAfterContent(rs.getString("after_content"));
        event.setReason(rs.getString("reason"));
        event.setSourceRef(rs.getString("source_ref"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            event.setCreatedAt(ts.toLocalDateTime());
        }
        return event;
    };

    /**
     * 追加一条决策事件；失败只记日志
     */
    public void insert(UserMemoryEvent event) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO user_memory_event
                                (memory_id, subject_key, decision, memory_type, memory_key,
                                 before_content, after_content, reason, source_ref, created_at)
                            VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    event.getMemoryId(), event.getSubjectKey(), event.getDecision(), event.getMemoryType(),
                    event.getMemoryKey(), event.getBeforeContent(), event.getAfterContent(), event.getReason(),
                    event.getSourceRef(),
                    Timestamp.valueOf(event.getCreatedAt() == null ? LocalDateTime.now() : event.getCreatedAt()));
        } catch (Exception e) {
            log.warn("记忆变更事件写入失败（不影响记忆本身）, decision: {}, reason: {}", event.getDecision(), e.getMessage());
        }
    }

    /**
     * 批量追加（清理任务一次产生大量失效决策时用，减少往返）
     */
    public void insertBatch(List<UserMemoryEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (UserMemoryEvent event : events) {
            insert(event);
        }
    }

    /**
     * 查询某主体的记忆变更历史（倒序），用于回溯"这条偏好为什么被覆盖"
     */
    public List<UserMemoryEvent> findBySubject(String subjectKey, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM user_memory_event WHERE subject_key = ? ORDER BY created_at DESC LIMIT ?",
                rowMapper, subjectKey, limit);
    }

    /**
     * 查询某条记忆的全部变更历史，用于单条记忆的时间线展示
     */
    public List<UserMemoryEvent> findByMemoryId(String memoryId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM user_memory_event WHERE memory_id = CAST(? AS uuid) ORDER BY created_at DESC LIMIT ?",
                rowMapper, memoryId, limit);
    }
}
