-- ============================================================
-- RAG 来源索引表（MySQL 业务库 wtlx）
--
-- 用途：记录每个检索来源（游记 tb_blog / 官方攻略 guides）的
--       内容指纹、切片数量与入库状态，用于幂等、重试与补偿。
--
-- 全部语句幂等（IF NOT EXISTS），可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS rag_source_index (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_type     VARCHAR(16)  NOT NULL COMMENT '来源类型：BLOG=游记，GUIDE=官方攻略',
    source_id    BIGINT       NOT NULL COMMENT '来源业务主键',
    content_hash VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '内容指纹（md5），用于变更判定',
    chunk_count  INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    error_msg    VARCHAR(500) NULL COMMENT '失败原因',
    retry_count  INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rag_source (doc_type, source_id),
    KEY idx_rag_source_status (status, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'RAG 来源索引';
