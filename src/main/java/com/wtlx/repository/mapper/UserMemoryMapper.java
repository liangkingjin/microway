package com.wtlx.repository.mapper;

import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.domain.entity.memory.enums.MemoryStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆数据访问（PostgreSQL）。
 * <p>
 * 全部 SQL 集中在本类，原因：向量字面量、部分唯一索引、窗口函数这些 PG 专有语义
 * 一旦散落到上层就容易写错，而它们恰恰是"去重 / 冲突"正确性的最后一道防线。
 * 上层（抽取、合并、检索、管理接口）只调用语义化方法，不拼 SQL。
 * <p>
 * 数据源一律 {@code aiJdbcTemplate}，{@code @Primary} 的是 MySQL 业务数据源——
 * 走错数据源的表现是"表不存在"，且会污染业务库。这也是 CLAUDE.md 里 AI 侧 PG 访问的硬约定。
 *
 * @author 金小梁
 */
@Slf4j
@Repository
public class UserMemoryMapper {

    /**
     * user_memory 全列（不含 embedding：检索侧不需要回读 1024 维向量，
     * 回读一次等于白拉 4KB，且 pgvector 的文本形式解析很慢）
     */
    private static final String COLS = """
            id, memory_id, subject_key, user_id, memory_type, memory_key, content, structured,
            confidence, importance, status, source_type, source_ref, evidence,
            valid_from, valid_to, superseded_by, version, hit_count, last_hit_at,
            content_hash, dedup_key, created_at, updated_at
            """.replaceAll("\\s+", " ").trim();

    /**
     * 带表别名 m 的全列，供多表/子查询场景使用
     */
    private static final String COLS_M = COLS.replaceAll("(^|, )", "$1m.");

    /**
     * 生效记忆的公共过滤条件：状态生效 + 未过期
     */
    private static final String ACTIVE_PREDICATE_M =
            " m.status = 'ACTIVE' AND (m.valid_to IS NULL OR m.valid_to > CURRENT_TIMESTAMP) ";

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /**
     * 基础行映射（无分数）
     */
    private final RowMapper<UserMemory> rowMapper = (rs, rowNum) -> mapMemory(rs);

    /**
     * 带分数行映射：要求 SQL 里选出 {@code path_score} 别名
     */
    private final RowMapper<ScoredMemory> scoredRowMapper =
            (rs, rowNum) -> new ScoredMemory(mapMemory(rs), rs.getDouble("path_score"));

    // ==================================================================
    // 写入
    // ==================================================================

    /**
     * 新增一条记忆。
     * <p>
     * 依赖 {@code uk_user_memory_active_dedup} 部分唯一索引兜底：并发下重复写入会抛
     * 唯一约束冲突，由调用方转成 NOOP——这是"抽取异步 + 多实例"场景下防止记忆重复的关键。
     *
     * @return 影响行数（1 成功）
     */
    public int insert(UserMemory memory) {
        String sql = """
                INSERT INTO user_memory
                    (memory_id, subject_key, user_id, memory_type, memory_key, content, structured,
                     confidence, importance, status, source_type, source_ref, evidence,
                     valid_from, valid_to, version, content_hash, dedup_key, embedding, created_at, updated_at)
                VALUES
                    (CAST(? AS uuid), ?, ?, ?, ?, ?, CAST(? AS jsonb), ?::real, ?::real, ?, ?, ?, ?,
                     ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?)
                """;
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update(sql,
                memory.getMemoryId(),
                memory.getSubjectKey(),
                memory.getUserId(),
                memory.getMemoryType(),
                memory.getMemoryKey(),
                memory.getContent(),
                memory.getStructured(),
                memory.getConfidence(),
                memory.getImportance(),
                memory.getStatus(),
                memory.getSourceType(),
                memory.getSourceRef(),
                memory.getEvidence(),
                toTimestamp(memory.getValidFrom() == null ? now : memory.getValidFrom()),
                toTimestamp(memory.getValidTo()),
                memory.getVersion() <= 0 ? 1 : memory.getVersion(),
                memory.getContentHash(),
                memory.getDedupKey(),
                memory.getEmbeddingLiteral(),
                toTimestamp(now),
                toTimestamp(now));
    }

    /**
     * 就地更新记忆内容（决策 UPDATE）：丰富表述、补结构化槽位、提升置信度，不产生新行
     */
    public int updateContent(long id, String content, String structured, double confidence,
                             double importance, int version, String evidence) {
        String sql = """
                UPDATE user_memory
                   SET content = ?, structured = CAST(? AS jsonb), confidence = ?::real, importance = ?::real,
                       version = ?, evidence = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """;
        return jdbcTemplate.update(sql, content, structured, confidence, importance, version, evidence, id);
    }

    /**
     * 重复信息巩固（决策 NOOP）：只提升置信度并追加证据，不动内容
     */
    public int confirm(long id, double confidence, String evidence) {
        String sql = """
                UPDATE user_memory
                   SET confidence = GREATEST(confidence, ?::real),
                       evidence = CASE WHEN ? IS NULL THEN evidence ELSE ? END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """;
        return jdbcTemplate.update(sql, confidence, evidence, evidence, id);
    }

    /**
     * 取代旧记忆（决策 SUPERSEDE）：旧行置 SUPERSEDED 并指向新记忆，同时把有效期收在当下，
     * 使其立刻退出检索——三重保证"改口后新偏好立即生效"
     */
    public int supersede(long id, String newMemoryId) {
        String sql = """
                UPDATE user_memory
                   SET status = 'SUPERSEDED',
                       superseded_by = CAST(? AS uuid),
                       valid_to = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'ACTIVE'
                """;
        return jdbcTemplate.update(sql, newMemoryId, id);
    }

    /**
     * 失效（决策 FORGET / 失效覆盖）：置 EXPIRED 并收口有效期
     */
    public int expire(long id) {
        String sql = """
                UPDATE user_memory
                   SET status = 'EXPIRED', valid_to = COALESCE(valid_to, CURRENT_TIMESTAMP),
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'ACTIVE'
                """;
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 按对外标识软删（用户主动清理单条）
     */
    public int markDeleted(String subjectKey, String memoryId) {
        String sql = """
                UPDATE user_memory
                   SET status = 'DELETED', valid_to = COALESCE(valid_to, CURRENT_TIMESTAMP),
                       updated_at = CURRENT_TIMESTAMP
                 WHERE subject_key = ? AND memory_id = CAST(? AS uuid) AND status = 'ACTIVE'
                """;
        return jdbcTemplate.update(sql, subjectKey, memoryId);
    }

    /**
     * 物理清空某主体的全部记忆（用户主动"忘记我"场景，含历史行）
     */
    public int deleteBySubject(String subjectKey) {
        return jdbcTemplate.update("DELETE FROM user_memory WHERE subject_key = ?", subjectKey);
    }

    // ==================================================================
    // 合并 / 冲突校验查询
    // ==================================================================

    /**
     * 冲突校验用：按向量相似度取同主体最近的生效记忆（不设阈值，由调用方按相似度分档决策）
     *
     * @param subjectKey 主体键
     * @param literal    pgvector 字面量
     * @param limit      条数
     * @return 命中（含余弦相似度作为 path_score）
     */
    public List<ScoredMemory> findSimilarActive(String subjectKey, String literal, int limit) {
        String sql = "SELECT " + COLS_M + ", (1 - (m.embedding <=> CAST(? AS vector))) AS path_score"
                + " FROM user_memory m"
                + " WHERE m.subject_key = ? AND" + ACTIVE_PREDICATE_M
                + "   AND m.embedding IS NOT NULL"
                + " ORDER BY m.embedding <=> CAST(? AS vector)"
                + " LIMIT ?";
        return jdbcTemplate.query(sql, scoredRowMapper, literal, subjectKey, literal, limit);
    }

    /**
     * 冲突校验用：同主体同槽位的全部生效记忆（同槽位不同取值即语义冲突，与相似度无关）
     */
    public List<UserMemory> findActiveByKey(String subjectKey, String memoryKey, int limit) {
        String sql = "SELECT " + COLS + " FROM user_memory"
                + " WHERE subject_key = ? AND memory_key = ? AND status = 'ACTIVE'"
                + "   AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP)"
                + " ORDER BY confidence DESC, updated_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, subjectKey, memoryKey, limit);
    }

    /**
     * 精确去重查询：同主体同 dedup_key 的生效记忆（几乎等同唯一索引命中）
     */
    public UserMemory findActiveByDedupKey(String subjectKey, String dedupKey) {
        List<UserMemory> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM user_memory WHERE subject_key = ? AND dedup_key = ? AND status = 'ACTIVE' LIMIT 1",
                rowMapper, subjectKey, dedupKey);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================================================================
    // 检索（单路向量）
    // ==================================================================

    /**
     * 语义向量召回：查询向量与记忆向量的余弦相似度排序，
     * 负责"用户换个说法也能命中同一偏好"
     *
     * @param literal       pgvector 字面量
     * @param topK          召回条数
     * @param minSimilarity 相似度下限
     */
    public List<ScoredMemory> searchByVector(String subjectKey, String literal, int topK, double minSimilarity) {
        String sql = "SELECT " + COLS_M + ", (1 - (m.embedding <=> CAST(? AS vector))) AS path_score"
                + " FROM user_memory m"
                + " WHERE m.subject_key = ? AND" + ACTIVE_PREDICATE_M
                + "   AND m.embedding IS NOT NULL"
                + "   AND (1 - (m.embedding <=> CAST(? AS vector))) >= ?::real"
                + " ORDER BY m.embedding <=> CAST(? AS vector)"
                + " LIMIT ?";
        return jdbcTemplate.query(sql, scoredRowMapper, literal, subjectKey, literal, minSimilarity, literal, topK);
    }

    // ==================================================================
    // 查询 / 统计（管理接口）
    // ==================================================================

    /**
     * 管理接口：按条件列举记忆
     *
     * @param type   记忆类型过滤，可为 null
     * @param status 状态过滤，可为 null 表示不限（含历史行）
     */
    public List<UserMemory> listBySubject(String subjectKey, String type, String status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS).append(" FROM user_memory WHERE subject_key = ?");
        List<Object> args = new ArrayList<>();
        args.add(subjectKey);
        if (type != null && !type.isBlank()) {
            sql.append(" AND memory_type = ?");
            args.add(type.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        sql.append(" ORDER BY importance DESC, confidence DESC, updated_at DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    /**
     * 生效记忆数量
     */
    public int countActive(String subjectKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memory WHERE subject_key = ? AND status = 'ACTIVE'",
                Integer.class, subjectKey);
        return count == null ? 0 : count;
    }

    /**
     * 生效记忆按类型分布，供管理接口展示"记住了什么"
     */
    public Map<String, Integer> countActiveByType(String subjectKey) {
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT memory_type, COUNT(*) AS c FROM user_memory WHERE subject_key = ? AND status = 'ACTIVE'"
                        + " GROUP BY memory_type ORDER BY c DESC",
                rs -> {
                    result.put(rs.getString("memory_type"), rs.getInt("c"));
                }, subjectKey);
        return result;
    }

    /**
     * 全库生效记忆类型分布（运维观测用）
     */
    public Map<String, Integer> countAllActiveByType() {
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT memory_type, COUNT(*) AS c FROM user_memory WHERE status = 'ACTIVE'"
                        + " GROUP BY memory_type ORDER BY c DESC",
                rs -> {
                    result.put(rs.getString("memory_type"), rs.getInt("c"));
                });
        return result;
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 行 → 实体；向量列刻意不映射
     */
    private static UserMemory mapMemory(ResultSet rs) throws SQLException {
        UserMemory memory = new UserMemory();
        memory.setId(rs.getLong("id"));
        memory.setMemoryId(rs.getString("memory_id"));
        memory.setSubjectKey(rs.getString("subject_key"));
        memory.setUserId(rs.getString("user_id"));
        memory.setMemoryType(rs.getString("memory_type"));
        memory.setMemoryKey(rs.getString("memory_key"));
        memory.setContent(rs.getString("content"));
        memory.setStructured(rs.getString("structured"));
        memory.setConfidence(rs.getDouble("confidence"));
        memory.setImportance(rs.getDouble("importance"));
        memory.setStatus(MemoryStatus.fromCode(rs.getString("status")).getCode());
        memory.setSourceType(rs.getString("source_type"));
        memory.setSourceRef(rs.getString("source_ref"));
        memory.setEvidence(rs.getString("evidence"));
        memory.setValidFrom(toLocalDateTime(rs.getTimestamp("valid_from")));
        memory.setValidTo(toLocalDateTime(rs.getTimestamp("valid_to")));
        memory.setSupersededBy(rs.getString("superseded_by"));
        memory.setVersion(rs.getInt("version"));
        memory.setHitCount(rs.getInt("hit_count"));
        memory.setLastHitAt(toLocalDateTime(rs.getTimestamp("last_hit_at")));
        memory.setContentHash(rs.getString("content_hash"));
        memory.setDedupKey(rs.getString("dedup_key"));
        memory.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        memory.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return memory;
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
