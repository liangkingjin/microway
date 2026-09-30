-- ============================================================
-- TripAgent PostgreSQL 统一建表脚本（数据库：vector_db）
--
-- 统一存储：
--   1. pgvector 向量表（vector_store）
--   2. ReactAgent 对话消息 / 摘要（conversation_message / conversation_summary）
--
-- 全部语句幂等（IF NOT EXISTS），可重复执行。
-- 应用侧 PGVector starter（initialize-schema=true）也会执行同样的幂等 DDL，互不冲突。
-- ============================================================

-- 1. 向量扩展与向量表（PGVector，text-embedding-v3 = 1024 维）
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1024)
);

-- HNSW 索引，余弦距离（与 application.yml：index-type=HNSW, distance-type=COSINE_DISTANCE 一致）
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- 2. 对话消息表（ReactAgent 历史消息）
CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 助手消息附带的检索卡片（TripCardVO 的 JSON 数组），用于重新进入会话时恢复卡片展示
ALTER TABLE conversation_message ADD COLUMN IF NOT EXISTS cards_json TEXT;

CREATE INDEX IF NOT EXISTS idx_conversation_message_conv_created
    ON conversation_message (conversation_id, created_at);

-- 3. 对话摘要表（压缩后的对话摘要）
CREATE TABLE IF NOT EXISTS conversation_summary (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL UNIQUE,
    summary TEXT NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
