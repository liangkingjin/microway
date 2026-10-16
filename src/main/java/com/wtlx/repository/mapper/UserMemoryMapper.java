package com.wtlx.repository.mapper;

import com.wtlx.domain.entity.memory.DuplicatePair;
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
 * 一旦散落到上层就容易写错，而它们恰恰是"去重 / 冲突 / 淘汰"正确性的最后一道防线。
 * 上层（抽取、合并、检索、清理）只调用语义化方法，不拼 SQL。
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
     * 带分数行映射：要求 SQL 里选出 {@code path_score} 或 {@code sim} 别名
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
     * 失效（决策 FORGET / 清理）：置 EXPIRED 并收口有效期
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

    /**
     * 命中统计批量回写：体现复用强度，也是清理时"是否还有人用得上"的依据
     *
     * @param memoryIds 命中的对外标识
     * @return 影响行数
     */
    public int bumpHitStats(List<String> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return 0;
        }
        String sql = """
                UPDATE user_memory
                   SET hit_count = hit_count + 1, last_hit_at = CURRENT_TIMESTAMP
                 WHERE memory_id::text = ANY(string_to_array(?, ','))
                """;
        return jdbcTemplate.update(sql, String.join(",", memoryIds));
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

    /**
     * 按对外标识查单条
     */
    public UserMemory findByMemoryId(String subjectKey, String memoryId) {
        List<UserMemory> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM user_memory WHERE subject_key = ? AND memory_id = CAST(? AS uuid)",
                rowMapper, subjectKey, memoryId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================================================================
    // 并行检索（语义路 + 关键词路）
    // ==================================================================

    /**
     * 语义路 · 画像兜底：按 {@code 置信度 × 重要度} 取高分记忆，<b>与查询无关</b>。
     * <p>
     * 这一段是"跨会话稳定复用"的保障：其余召回手段都以"与本轮问句相关"为前提，
     * 于是会出现一个反直觉的问题——用户新开会话问"帮我规划一下行程"，
     * 问句里没有任何偏好线索，语义与关键词两条路都召不回"不吃辣"这条老记忆，
     * 而它明明最有价值。取高分记忆的目的就是把它顶上来。
     * <p>
     * 返回的是"高分记忆"而非"稳定层记忆"：排序键与类型无关，调用方按
     * {@code MemoryType.stable} 标记自行过滤，避免在 SQL 里硬编码类型清单。
     */
    public List<ScoredMemory> searchStable(String subjectKey, int topK) {
        String sql = "SELECT " + COLS_M + ", (m.confidence * m.importance) AS path_score"
                + " FROM user_memory m"
                + " WHERE m.subject_key = ? AND" + ACTIVE_PREDICATE_M
                + " ORDER BY (m.confidence * m.importance) DESC, m.updated_at DESC"
                + " LIMIT ?";
        return jdbcTemplate.query(sql, scoredRowMapper, subjectKey, topK);
    }

    /**
     * 语义路 · 向量召回：查询向量与记忆向量的余弦相似度排序，
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

    /**
     * 关键词路：按 n-gram 命中数排序。
     * embedding 服务不可用或中文专有名词（"稻城亚丁"）被向量稀释时，这条路保证召回
     *
     * @param keywordCsv 逗号分隔关键词（由 {@code TextKeywords} 抽取）
     */
    public List<ScoredMemory> searchByKeywords(String subjectKey, String keywordCsv, int topK) {
        String sql = "SELECT " + COLS_M
                + "     , (SELECT COUNT(*) FROM unnest(string_to_array(?, ',')) AS k"
                + "         WHERE m.content ILIKE '%' || k || '%') AS path_score"
                + " FROM user_memory m"
                + " WHERE m.subject_key = ? AND" + ACTIVE_PREDICATE_M
                + "   AND EXISTS (SELECT 1 FROM unnest(string_to_array(?, ',')) AS k"
                + "                WHERE m.content ILIKE '%' || k || '%')"
                + " ORDER BY path_score DESC, m.confidence DESC"
                + " LIMIT ?";
        return jdbcTemplate.query(sql, scoredRowMapper, keywordCsv, subjectKey, keywordCsv, topK);
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
     * 全库生效记忆类型分布（清理任务与运维观测用）
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
    // 清理
    // ==================================================================

    /**
     * 过期清理：有效期已到的生效记忆统一失效（全局或指定主体）
     *
     * @param subjectKey 主体键；为 null 表示全库
     */
    public int expireByValidTo(String subjectKey) {
        String sql = "UPDATE user_memory SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP"
                + " WHERE status = 'ACTIVE' AND valid_to IS NOT NULL AND valid_to <= CURRENT_TIMESTAMP"
                + (subjectKey == null ? "" : " AND subject_key = ?");
        return subjectKey == null
                ? jdbcTemplate.update(sql)
                : jdbcTemplate.update(sql, subjectKey);
    }

    /**
     * 低置信淘汰：从未被命中且已冷置超过阈值天数的低置信记忆。
     * 双重条件（低置信 + 冷置 + 零命中）避免误杀刚写入、还没机会被命中的新记忆
     *
     * @param threshold  置信度阈值
     * @param staleDays  冷置天数
     * @param subjectKey 主体键；为 null 表示全库
     */
    public int expireLowConfidence(double threshold, int staleDays, String subjectKey) {
        String sql = "UPDATE user_memory SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP"
                + " WHERE status = 'ACTIVE' AND confidence < ?::real AND last_hit_at IS NULL"
                + "   AND created_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')"
                + (subjectKey == null ? "" : " AND subject_key = ?");
        return subjectKey == null
                ? jdbcTemplate.update(sql, threshold, staleDays)
                : jdbcTemplate.update(sql, threshold, staleDays, subjectKey);
    }

    /**
     * 容量控制：单主体生效记忆超出上限时，按 置信度×重要度 降序保留，其余失效。
     * 用窗口函数一次完成，避免"查出来再逐条更新"的 N+1
     */
    public int expireOverCap(String subjectKey, int cap) {
        String sql = """
                UPDATE user_memory SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                 WHERE id IN (
                     SELECT id FROM (
                         SELECT id, ROW_NUMBER() OVER (
                                    ORDER BY (confidence * importance) DESC, hit_count DESC, updated_at DESC
                                ) AS rn
                           FROM user_memory
                          WHERE subject_key = ? AND status = 'ACTIVE'
                     ) ranked WHERE ranked.rn > ?
                 )
                """;
        return jdbcTemplate.update(sql, subjectKey, cap);
    }

    /**
     * 冗余合并清理：按向量相似度找出"表述不同、事实相同"的近重复记忆对。
     * <p>
     * 保留方在 SQL 侧直接定好（置信度更高；置信度相同则表述更完整），
     * 只查单向（{@code keep → drop}），另一半由被测排除，避免同一对被处理两次。
     * <p>
     * 之所以不用"同槽位只留一条"的粗暴规则：槽位是聚合维度而非唯一键，
     * 同一个 {@code trip.plan} 槽位下"明年暑假去云南""国庆去西安"是两条都该保留的合法记忆。
     *
     * @param subjectKey    主体键
     * @param minSimilarity 判定为近重复的相似度下限
     * @param limit         单次返回对数上限
     * @return 近重复记忆对，按相似度降序
     */
    public List<DuplicatePair> findDuplicatePairs(String subjectKey, double minSimilarity, int limit) {
        String sql = """
                SELECT a.memory_id AS keep_memory_id,
                       b.memory_id AS drop_memory_id,
                       1 - (a.embedding <=> b.embedding) AS similarity
                  FROM user_memory a
                  JOIN user_memory b
                    ON a.subject_key = b.subject_key
                   AND a.id < b.id
                   AND b.status = 'ACTIVE'
                   AND (b.valid_to IS NULL OR b.valid_to > CURRENT_TIMESTAMP)
                 WHERE a.subject_key = ?
                   AND a.status = 'ACTIVE'
                   AND (a.valid_to IS NULL OR a.valid_to > CURRENT_TIMESTAMP)
                   AND a.embedding IS NOT NULL
                   AND b.embedding IS NOT NULL
                   -- a 为保留方：置信度更高，或置信度相同但表述更完整
                   AND (a.confidence > b.confidence
                        OR (a.confidence = b.confidence AND LENGTH(a.content) >= LENGTH(b.content)))
                   AND (1 - (a.embedding <=> b.embedding)) >= ?::real
                 ORDER BY similarity DESC
                 LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new DuplicatePair(
                        rs.getString("keep_memory_id"),
                        rs.getString("drop_memory_id"),
                        rs.getDouble("similarity")),
                subjectKey, minSimilarity, limit);
    }

    /**
     * 冗余合并清理（按槽位精确重复的兜底）：同主体同槽位且内容完全一致的历史残留。
     * <p>
     * 正常情况下 {@code uk_user_memory_active_dedup} 已保证不会出现，此方法用于修复
     * "唯一索引上线前写入的脏数据"以及手工直改数据库造成的不一致。
     */
    public int expireExactDuplicateBySlot(String subjectKey) {
        String sql = """
                UPDATE user_memory SET status = 'SUPERSEDED', updated_at = CURRENT_TIMESTAMP
                 WHERE id IN (
                     SELECT id FROM (
                         SELECT id, ROW_NUMBER() OVER (
                                    PARTITION BY memory_key, content_hash
                                    ORDER BY confidence DESC, updated_at DESC
                                ) AS rn
                           FROM user_memory
                          WHERE subject_key = ? AND status = 'ACTIVE'
                     ) ranked WHERE ranked.rn > 1
                 )
                """;
        return jdbcTemplate.update(sql, subjectKey);
    }

    /**
     * 全部存有生效记忆的主体（清理任务遍历用）
     */
    public List<String> listActiveSubjects(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT subject_key FROM user_memory WHERE status = 'ACTIVE'"
                        + " GROUP BY subject_key ORDER BY subject_key LIMIT ?",
                String.class, limit);
    }

    /**
     * 物理清除：非生效状态的记忆超过保留期后彻底删除。
     * 软删是为了审计，但审计价值有时效——90 天前的"被取代"记录既占空间又拖慢部分索引
     */
    public int purgeSoftDeleted(int retentionDays) {
        return jdbcTemplate.update(
                "DELETE FROM user_memory WHERE status <> 'ACTIVE'"
                        + " AND updated_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')",
                retentionDays);
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
