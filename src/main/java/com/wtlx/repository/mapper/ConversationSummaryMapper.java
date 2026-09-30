package com.wtlx.repository.mapper;

import com.wtlx.domain.entity.conversation.ConversationSummary;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 对话摘要数据访问
 * 使用 JdbcTemplate 避免 MyBatis Plus 兼容性问题
 */
@Repository
public class ConversationSummaryMapper {

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ConversationSummary> rowMapper = (rs, rowNum) -> {
        ConversationSummary summary = new ConversationSummary();
        summary.setId(rs.getLong("id"));
        summary.setConversationId(rs.getString("conversation_id"));
        summary.setSummary(rs.getString("summary"));
        summary.setMessageCount(rs.getInt("message_count"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            summary.setCreatedAt(ts.toLocalDateTime());
        }
        Timestamp ts2 = rs.getTimestamp("updated_at");
        if (ts2 != null) {
            summary.setUpdatedAt(ts2.toLocalDateTime());
        }
        return summary;
    };

    public ConversationSummary findByConversationId(String conversationId) {
        List<ConversationSummary> list = jdbcTemplate.query(
                "SELECT * FROM conversation_summary WHERE conversation_id = ? ORDER BY updated_at DESC LIMIT 1",
                rowMapper, conversationId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void insert(ConversationSummary summary) {
        jdbcTemplate.update(
                "INSERT INTO conversation_summary (conversation_id, summary, message_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                summary.getConversationId(), summary.getSummary(), summary.getMessageCount(),
                Timestamp.valueOf(summary.getCreatedAt()), Timestamp.valueOf(summary.getUpdatedAt()));
    }

    public void update(ConversationSummary summary) {
        jdbcTemplate.update(
                "UPDATE conversation_summary SET summary = ?, message_count = ?, updated_at = ? WHERE id = ?",
                summary.getSummary(), summary.getMessageCount(),
                Timestamp.valueOf(summary.getUpdatedAt()), summary.getId());
    }

    public void upsert(ConversationSummary summary) {
        ConversationSummary existing = findByConversationId(summary.getConversationId());
        if (existing != null) {
            existing.setSummary(summary.getSummary());
            existing.setMessageCount(summary.getMessageCount());
            existing.setUpdatedAt(summary.getUpdatedAt());
            update(existing);
        } else {
            insert(summary);
        }
    }

    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM conversation_summary WHERE conversation_id = ?", conversationId);
    }
}
