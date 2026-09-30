package com.wtlx.repository.mapper;

import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.conversation.enums.MessageRole;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 对话消息数据访问
 * 使用 JdbcTemplate 避免 MyBatis Plus 兼容性问题
 */
@Repository
public class ConversationMessageMapper {

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<ConversationMessage> rowMapper = (rs, rowNum) -> {
        ConversationMessage msg = new ConversationMessage();
        msg.setId(rs.getLong("id"));
        msg.setConversationId(rs.getString("conversation_id"));
        msg.setRole(MessageRole.fromCode(rs.getString("role")));
        msg.setContent(rs.getString("content"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            msg.setCreatedAt(ts.toLocalDateTime());
        }
        return msg;
    };

    public List<ConversationMessage> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
                "SELECT * FROM conversation_message WHERE conversation_id = ? ORDER BY created_at ASC",
                rowMapper, conversationId);
    }

    public List<ConversationMessage> findRecentByConversationId(String conversationId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM conversation_message WHERE conversation_id = ? ORDER BY created_at DESC LIMIT ?",
                rowMapper, conversationId, limit);
    }

    public int countUserMessages(String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ? AND role = 'USER'",
                Integer.class, conversationId);
        return count != null ? count : 0;
    }

    public int countByConversationId(String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ?",
                Integer.class, conversationId);
        return count != null ? count : 0;
    }

    public void insert(ConversationMessage message) {
        jdbcTemplate.update(
                "INSERT INTO conversation_message (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                message.getConversationId(), message.getRole().getCode(), message.getContent(),
                Timestamp.valueOf(message.getCreatedAt()));
    }

    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM conversation_message WHERE conversation_id = ?", conversationId);
    }

    public void deleteByConversationIdBefore(String conversationId, java.time.LocalDateTime before) {
        jdbcTemplate.update(
                "DELETE FROM conversation_message WHERE conversation_id = ? AND created_at < ?",
                conversationId, Timestamp.valueOf(before));
    }
}
