package com.wtlx.infrastructure.memory.schema;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆表结构初始化器。
 * <p>
 * 项目没有 Flyway/Liquibase，DDL 一律手工执行的幂等脚本（{@code db/schema-postgresql.sql}）。
 * 这里把同一份幂等 DDL 在启动期再跑一遍，理由与 PGVector 的 {@code initialize-schema: true} 一致：
 * 新增能力不应该要求部署方记得额外执行一次脚本——漏执行的表现是"记忆静默不生效"，
 * 排查成本远高于启动时多几条 DDL。
 * <p>
 * <b>失败策略是"记日志不阻断启动"</b>：PG 暂时不可用时服务仍应能提供不含长期记忆的对话能力
 * （会话内短期记忆存在，业务不受影响）。因此逐条执行、逐条兜异常，最后汇总告警，
 * 并保留 {@link #ensureSchema()} 供管理接口在 PG 恢复后手动补建。
 *
 * @author 金小梁
 */
@Slf4j
@Component
@Order(20)
public class MemorySchemaInitializer implements ApplicationRunner {

    /**
     * 幂等 DDL 语句，与 {@code resources/db/schema-postgresql.sql} 的第 4~6 节保持一致。
     * 拆成数组而非整段脚本：JdbcTemplate 不支持多语句，且逐条执行能把失败范围限制在单条语句内。
     */
    private static final String[] DDL_STATEMENTS = {
            // ---- 依赖扩展（pgvector 库本身可能已由 vector_store 初始化建好，重复执行无副作用）----
            "CREATE EXTENSION IF NOT EXISTS vector",
            "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"",

            // ---- 4. 用户长期记忆主表 ----
            """
            CREATE TABLE IF NOT EXISTS user_memory (
                id             BIGSERIAL PRIMARY KEY,
                memory_id      UUID         NOT NULL DEFAULT uuid_generate_v4(),
                subject_key    VARCHAR(80)  NOT NULL,
                user_id        VARCHAR(64),
                memory_type    VARCHAR(24)  NOT NULL,
                memory_key     VARCHAR(96)  NOT NULL,
                content        TEXT         NOT NULL,
                structured     JSONB,
                confidence     REAL         NOT NULL DEFAULT 0.6,
                importance     REAL         NOT NULL DEFAULT 0.5,
                status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                source_type    VARCHAR(24)  NOT NULL DEFAULT 'CONVERSATION',
                source_ref     VARCHAR(160),
                evidence       TEXT,
                valid_from     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                valid_to       TIMESTAMP,
                superseded_by  UUID,
                version        INTEGER      NOT NULL DEFAULT 1,
                hit_count      INTEGER      NOT NULL DEFAULT 0,
                last_hit_at    TIMESTAMP,
                content_hash   VARCHAR(64)  NOT NULL,
                dedup_key      VARCHAR(200) NOT NULL,
                embedding      vector(1024),
                created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memory_active_dedup
                ON user_memory (subject_key, dedup_key) WHERE status = 'ACTIVE'
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_subject_status
                ON user_memory (subject_key, status, memory_type)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_valid
                ON user_memory (subject_key, status, valid_to)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_subject_key
                ON user_memory (subject_key, memory_key, status)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_hash
                ON user_memory (subject_key, content_hash)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_embedding
                ON user_memory USING hnsw (embedding vector_cosine_ops)
            """,

            // ---- 5. 变更事件审计 ----
            """
            CREATE TABLE IF NOT EXISTS user_memory_event (
                id             BIGSERIAL PRIMARY KEY,
                memory_id      UUID,
                subject_key    VARCHAR(80) NOT NULL,
                decision       VARCHAR(16) NOT NULL,
                memory_type    VARCHAR(24),
                memory_key     VARCHAR(96),
                before_content TEXT,
                after_content  TEXT,
                reason         TEXT,
                source_ref     VARCHAR(160),
                created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_event_subject
                ON user_memory_event (subject_key, created_at DESC)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_user_memory_event_memory
                ON user_memory_event (memory_id)
            """,

            // ---- 6. 抽取台账（幂等）----
            """
            CREATE TABLE IF NOT EXISTS user_memory_extraction (
                id              BIGSERIAL PRIMARY KEY,
                subject_key     VARCHAR(80) NOT NULL,
                conversation_id VARCHAR(64) NOT NULL,
                turn_hash       VARCHAR(64) NOT NULL,
                candidate_count INTEGER     NOT NULL DEFAULT 0,
                applied_count   INTEGER     NOT NULL DEFAULT 0,
                created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memory_extraction_turn
                ON user_memory_extraction (subject_key, conversation_id, turn_hash)
            """
    };

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate aiJdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureSchema();
    }

    /**
     * 执行幂等 DDL。
     *
     * @return 执行失败的语句数量；0 表示全部成功
     */
    public int ensureSchema() {
        List<String> failures = new ArrayList<>();
        for (String ddl : DDL_STATEMENTS) {
            try {
                aiJdbcTemplate.execute(ddl);
            } catch (Exception e) {
                // 单条失败不中断：CREATE EXTENSION 需要超级用户，普通账号下失败属预期，
                // 只要扩展已由 DBA 建好，后续建表语句仍能正常执行
                failures.add(firstLine(ddl) + " → " + e.getMessage());
            }
        }
        if (failures.isEmpty()) {
            log.info("用户长期记忆表结构初始化完成，共 {} 条 DDL", DDL_STATEMENTS.length);
            return 0;
        }
        log.warn("用户长期记忆表结构初始化存在 {} 条失败（不影响启动，可在 PG 恢复后重试）；"
                        + "若长期记忆相关接口报错，请手工执行 resources/db/schema-postgresql.sql：\n  {}",
                failures.size(), String.join("\n  ", failures));
        return failures.size();
    }

    private String firstLine(String ddl) {
        String trimmed = ddl.trim();
        int idx = trimmed.indexOf('\n');
        return idx > 0 ? trimmed.substring(0, idx) : trimmed;
    }
}
