-- ============================================================
-- 用户长期记忆（跨会话）建表脚本 —— user_memory / user_memory_extraction
--
-- 本文件是这两张表的【唯一来源】：
--   1) 启动期由 MemorySchemaInitializer 读取并逐条执行（幂等，失败只告警不阻断启动）；
--   2) 也可手工执行做兜底补建：psql -f db/schema-user-memory.sql
-- 因此不要把这些 DDL 再抄进 schema-postgresql.sql —— 两份副本必然漂移，
-- 而漂移的表现是"接口报错但代码看起来没问题"。
--
-- 全部语句幂等，可重复执行。
-- 前两条 CREATE EXTENSION 是本节自包含所需（vector 列类型 / uuid_generate_v4），
-- 与 schema-postgresql.sql 第 1 节重叠属于预期：它们本身就是 no-op 守卫。
--
-- 作用域列是 user_id（BIGINT）：长期记忆按"人"隔离，跨 threadId、跨会话稳定复用。
-- 历史版本用 subject_key（形如 'user:<userId>'）做作用域；本文件内联了
-- "subject_key -> user_id" 的一次性迁移（DO 块），老库升级与新库初始化共用同一份脚本。
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
--   c) ACTIVE 部分唯一索引 —— 同一用户同一槽位同一取值至多一条生效记忆，
--      数据库层兜住并发重复写入（抽取是异步的，同一用户可能被多轮任务同时处理）；
--   d) valid_to —— 时效性记忆（"下个月去成都"）到点自动退出检索，无需人工干预。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_memory (
    id             BIGSERIAL PRIMARY KEY,
    -- 对外稳定标识：取代关系、事件审计、按条删除都走它，不暴露自增 id
    memory_id      UUID         NOT NULL DEFAULT uuid_generate_v4(),
    -- 记忆作用域：登录用户 id，长期记忆的隔离边界
    user_id        BIGINT       NOT NULL,
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

-- 老库升级：把历史 subject_key('user:<userId>') 迁移为 user_id(BIGINT)。
-- 幂等：列已迁移过（无 subject_key）时整个块跳过；新库直接由上面的 CREATE 建好。
DO $$
DECLARE
    has_subject_key boolean := false;
    has_user_id boolean := false;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'user_memory' AND column_name = 'subject_key'
    ) INTO has_subject_key;
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'user_memory' AND column_name = 'user_id'
    ) INTO has_user_id;
    IF has_subject_key AND NOT has_user_id THEN
        ALTER TABLE user_memory ADD COLUMN user_id BIGINT;
    END IF;
    IF has_subject_key THEN
        UPDATE user_memory
           SET user_id = CAST(substring(subject_key from 6) AS BIGINT)
         WHERE user_id IS NULL AND subject_key LIKE 'user:%';
        ALTER TABLE user_memory DROP COLUMN subject_key;
    END IF;
    IF has_subject_key OR has_user_id THEN
        IF NOT EXISTS (SELECT 1 FROM user_memory WHERE user_id IS NULL) THEN
            ALTER TABLE user_memory ALTER COLUMN user_id SET NOT NULL;
        END IF;
    END IF;
END $$;

-- user_id 类型守卫：上面的迁移块只检查"列是否存在"、不检查类型。
-- 历史上个别库曾把 user_id 建成 VARCHAR（等值比较变成 varchar = bigint，直接 bad grammar）。
-- 类型不对时按文本就地转 BIGINT 并补 NOT NULL；列存在且已是 bigint 时整块跳过。
DO $$
DECLARE
    t text;
    nullable text;
BEGIN
    SELECT data_type, is_nullable INTO t, nullable FROM information_schema.columns
    WHERE table_name = 'user_memory' AND column_name = 'user_id';
    IF t IS NOT NULL AND t <> 'bigint' THEN
        EXECUTE format(
            'ALTER TABLE user_memory ALTER COLUMN user_id TYPE BIGINT USING NULLIF(user_id::text, %L)::bigint',
            '');
    END IF;
    IF t IS NOT NULL AND nullable = 'YES'
       AND NOT EXISTS (SELECT 1 FROM user_memory WHERE user_id IS NULL) THEN
        ALTER TABLE user_memory ALTER COLUMN user_id SET NOT NULL;
    END IF;
END $$;

-- 生效记忆的唯一去重约束（部分索引：只约束 ACTIVE，被取代的历史行可保留同名键）
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memory_active_dedup
    ON user_memory (user_id, dedup_key) WHERE status = 'ACTIVE';

-- 检索主路径：用户 + 状态 + 类型
CREATE INDEX IF NOT EXISTS idx_user_memory_subject_status
    ON user_memory (user_id, status, memory_type);

-- 时效过滤：用户 + 状态 + 有效期
CREATE INDEX IF NOT EXISTS idx_user_memory_valid
    ON user_memory (user_id, status, valid_to);

-- 冲突校验：按用户 + 槽位精确定位（同一槽位的不同取值即潜在冲突）
CREATE INDEX IF NOT EXISTS idx_user_memory_subject_key
    ON user_memory (user_id, memory_key, status);

-- 抽取台账幂等：按内容指纹查重
CREATE INDEX IF NOT EXISTS idx_user_memory_hash
    ON user_memory (user_id, content_hash);

-- 向量路：HNSW + 余弦距离（与 vector_store 的索引参数保持一致）
CREATE INDEX IF NOT EXISTS idx_user_memory_embedding
    ON user_memory USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- 5. 记忆抽取台账 —— user_memory_extraction
-- 抽取是异步且可能重试的，用 (用户 + 会话 + 轮次指纹) 唯一约束保证
-- "同一轮对话只抽取一次"，避免重试把同一句话反复写进长期记忆。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_memory_extraction (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    -- 段指纹：conversationId + 段内消息 id 序列的哈希
    turn_hash       VARCHAR(64) NOT NULL,
    candidate_count INTEGER     NOT NULL DEFAULT 0,
    applied_count   INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 老库升级：抽取台账同样把 subject_key 迁移为 user_id
DO $$
DECLARE
    has_subject_key boolean := false;
    has_user_id boolean := false;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'user_memory_extraction' AND column_name = 'subject_key'
    ) INTO has_subject_key;
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'user_memory_extraction' AND column_name = 'user_id'
    ) INTO has_user_id;
    IF has_subject_key AND NOT has_user_id THEN
        ALTER TABLE user_memory_extraction ADD COLUMN user_id BIGINT;
    END IF;
    IF has_subject_key THEN
        UPDATE user_memory_extraction
           SET user_id = CAST(substring(subject_key from 6) AS BIGINT)
         WHERE user_id IS NULL AND subject_key LIKE 'user:%';
        ALTER TABLE user_memory_extraction DROP COLUMN subject_key;
    END IF;
    IF has_subject_key OR has_user_id THEN
        IF NOT EXISTS (SELECT 1 FROM user_memory_extraction WHERE user_id IS NULL) THEN
            ALTER TABLE user_memory_extraction ALTER COLUMN user_id SET NOT NULL;
        END IF;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_memory_extraction_turn
    ON user_memory_extraction (user_id, conversation_id, turn_hash);

-- 抽取台账的 user_id 类型守卫（与主表同因：历史库可能建成 VARCHAR）
DO $$
DECLARE
    t text;
BEGIN
    SELECT data_type INTO t FROM information_schema.columns
    WHERE table_name = 'user_memory_extraction' AND column_name = 'user_id';
    IF t IS NOT NULL AND t <> 'bigint' THEN
        EXECUTE format(
            'ALTER TABLE user_memory_extraction ALTER COLUMN user_id TYPE BIGINT USING NULLIF(user_id::text, %L)::bigint',
            '');
    END IF;
END $$;
