-- 行程条目关联景点（业务库 MySQL）。
-- 作用：AI 通过 addItineraryItems 添加行程时显式带上景点名，前端据此把行程里的
-- 景点名渲染成可点击入口，点开进入「景点详情」（介绍/建议由 AI 基于站内资料生成）。
--
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，故用 information_schema 判断后再执行，
-- 脚本可重复运行。启动期由 ItinerarySpotSchemaInitializer 自动执行同一逻辑。
SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'itinerary_items'
      AND column_name = 'spot_name'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE itinerary_items ADD COLUMN spot_name VARCHAR(128) NULL COMMENT ''关联景点名，用于点开景点详情'' AFTER content',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
