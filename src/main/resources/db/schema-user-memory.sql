-- ============================================================
-- 用户长期记忆（跨会话）建表脚本 —— user_memory / user_memory_event / user_memory_extraction
--
-- 本文件是这三张表的【唯一来源】：
--   1) 启动期由 MemorySchemaInitializer 读取并逐条执行（幂等，失败只告警不阻断启动）；
--   2) 也可手工执行做兜底补建：psql -f db/schema-user-memory.sql
-- 因此不要把这些 DDL 再抄进 schema-postgresql.sql —— 两份副本必然漂移，
-- 而漂移的表现是"接口报错但代码看起来没问题"。
--
-- 全部语句幂等（IF NOT EXISTS），可重复执行。
-- 前两条 CREATE EXTENSION 是本节自包含所需（vector 列类型 / uuid_generate_v4），
-- 与 schema-postgresql.sql 第 1 节重叠属于预期：它们本身就是 no-op 守卫。
--
-- 与会话内短期记忆（conversation_message / conversation_summary）的根本差别：
-- 那两张表按 conversation_id（会话）隔离，本文件按 subject_key（人）隔离，
-- 故能跨 threadId、跨会话稳定复用。
-- ============================================================

-- 依赖扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 4. 用户长期记忆主表 —— user_memory
--
-- 设计要点：
--   a) embedding vector(1024) 与业务列同表 —— 一次 SQL 即可同时完成"向量排序 + 属性过滤"，
--      不必像 vector_store 那样再把 id 回表捞业务字段；
--   b) status 软删 + superseded_by —— 冲突覆盖必须留痕，否则偏好被误覆盖后无法回溯；
--   c) ACTIVE 部分唯一索引 —— 同一主体同一槽位同一取值至多一条生效记忆，
--      数据库层兜住并发重复写入（抽取是异步的，同一用户可能被多轮任务同时处理）；
--   d) valid_to —— 时效性记忆（"下个月去成都"）到点自动退出检索，无需人工干预。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_memory (
    id             BIGSERIAL PRIMARY KEY,
    -- 对外稳定标识：取代关系、事件审计、按条删除都走它，不暴露自增 id
    memory_id      UUID         NOT NULL DEFAULT uuid_generate_v4(),
    -- 记忆主体键：user:<userId> / guest:<clientId>，长期记忆的作用域边界
    subject_key    VARCHAR(80)  NOT NULL,
    user_id        VARCHAR(64),
    memory_type    VARCHAR(24)  NOT NULL,
    -- 归一化槽位键：同一槽位的不同取值即"潜在冲突"
    memory_key     VARCHAR(96)  NOT NULL,
    content        TEXT         NOT NULL,
    structured     JSONB,
    confidence     REAL         NOT NULL DEFAULT 0.6,
    importance     REAL         NOT NULL DEFAULT 0.5,
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    source_type    VARCHAR(24)  NOT NULL DEFAULT 'CONVERSATION',
    source_ref     VARCHAR(160),
    -- 抽取依据原文，冲突判定与人工复核时回溯用
    evidence       TEXT,
    valid_from     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- NULL = 长期有效（稳定层记忆）
    valid_to       TIMESTAMP,
    superseded_by  UUID,
    version        INTEGER      NOT NULL DEFAULT 1,
    hit_count      INTEGER      NOT NULL DEFAULT 0,
    last_hit_at    TIMESTAMP,
    content_hash   VARCHAR(64)  NOT NULL,
    -- type|key|valueHash，与 ACTIVE 部分唯一索引配合实现库级去重
    dedup_key      VARCHAR(200) NOT NULL,
    embedding      vector(1024),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 生效记忆的唯一去重约束（部分索引：只约束 ACTIVE，被取代的历史行可保留同名键）
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memory_active_dedup
    ON user_memory (subject_key, dedup_key) WHERE status = 'ACTIVE';

-- 检索主路径：主体 + 状态 + 类型
CREATE INDEX IF NOT EXISTS idx_user_memory_subject_status
    ON user_memory (subject_key, status, memory_type);

-- 时效过滤：主体 + 状态 + 有效期
CREATE INDEX IF NOT EXISTS idx_user_memory_valid
    ON user_memory (subject_key, status, valid_to);

-- 冲突校验：按主体 + 槽位精确定位（同一槽位的不同取值即潜在冲突）
CREATE INDEX IF NOT EXISTS idx_user_memory_subject_key
    ON user_memory (subject_key, memory_key, status);

-- 抽取台账幂等：按内容指纹查重
CREATE INDEX IF NOT EXISTS idx_user_memory_hash
    ON user_memory (subject_key, content_hash);

-- 向量路：HNSW + 余弦距离（与 vector_store 的索引参数保持一致）
CREATE INDEX IF NOT EXISTS idx_user_memory_embedding
    ON user_memory USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- 5. 记忆变更事件（审计流）—— user_memory_event
-- 记录每一次 ADD/UPDATE/SUPERSEDE/FORGET/NOOP/CLEANUP 决策，
-- 回答"这条记忆为什么被覆盖或失效"，也是抽取质量的统计口径。
-- ============================================================
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
);

CREATE INDEX IF NOT EXISTS idx_user_memory_event_subject
    ON user_memory_event (subject_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_memory_event_memory
    ON user_memory_event (memory_id);

-- ============================================================
-- 6. 记忆抽取台账 —— user_memory_extraction
-- 抽取是异步且可能重试的，用 (主体 + 会话 + 轮次指纹) 唯一约束保证
-- "同一轮对话只抽取一次"，避免重试把同一句话反复写进长期记忆。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_memory_extraction (
    id              BIGSERIAL PRIMARY KEY,
    subject_key     VARCHAR(80) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    -- 轮次指纹：sha256(conversationId|userMessage)
    turn_hash       VARCHAR(64) NOT NULL,
    candidate_count INTEGER     NOT NULL DEFAULT 0,
    applied_count   INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memory_extraction_turn
    ON user_memory_extraction (subject_key, conversation_id, turn_hash);
