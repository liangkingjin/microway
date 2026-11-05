-- 景点 AI 详情表：缓存大模型生成的介绍/游览建议与文生图图片的本地 URL。
-- 幂等脚本，由 ScenicInfoSchemaInitializer 在启动期执行（模式同 schema-user-memory.sql）。
CREATE TABLE IF NOT EXISTS scenic_spot_ai_info (
    id         BIGSERIAL PRIMARY KEY,
    spot_key   VARCHAR(128) NOT NULL,
    name       VARCHAR(128) NOT NULL,
    intro      TEXT,
    tips       TEXT,
    images     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_scenic_spot_ai_info_key UNIQUE (spot_key)
);
