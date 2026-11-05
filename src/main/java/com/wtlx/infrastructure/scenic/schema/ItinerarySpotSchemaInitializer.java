package com.wtlx.infrastructure.scenic.schema;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 行程表扩展列初始化（业务库 MySQL）：{@code spot_name} 与 {@code item_type}。
 * <p>
 * 为什么需要它：实体 {@code ItineraryItems} 新增了这两个字段，若部署环境漏执行 DDL，
 * MyBatis-Plus 的查询会因"列不存在"整体失败——表现是整个行程列表打不开，
 * 排查成本远高于启动时多两次 information_schema 查询。
 * <p>
 * 迁移逻辑与 {@code db/schema-itinerary-spot-mysql.sql} 完全一致（MySQL 不支持
 * {@code ADD COLUMN IF NOT EXISTS}，所以先判断再加）。用 LinkedHashMap 固定顺序：
 * {@code item_type} 的 {@code AFTER spot_name} 依赖前者先建出来。失败只告警不阻断启动。
 *
 * @author 金小梁
 */
@Slf4j
@Component
@Order(22)
public class ItinerarySpotSchemaInitializer implements ApplicationRunner {

    /** 列名 → 建列语句，顺序即执行顺序 */
    private static final Map<String, String> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("spot_name",
                "ALTER TABLE itinerary_items ADD COLUMN spot_name VARCHAR(128) NULL "
                        + "COMMENT '关联景点名，用于点开景点详情' AFTER content");
        COLUMNS.put("item_type",
                "ALTER TABLE itinerary_items ADD COLUMN item_type VARCHAR(16) NULL "
                        + "COMMENT '行程类型：SCENIC/TRANSPORT/HOTEL/DINING/OTHER' AFTER spot_name");
    }

    private static final String COLUMN_CHECK_SQL =
            "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'itinerary_items' "
                    + "AND column_name = ?";

    /** 主数据源（@Primary），行程表在业务 MySQL 上 */
    @Resource
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbcTemplate;
        try {
            jdbcTemplate = new JdbcTemplate(dataSource);
        } catch (Exception e) {
            log.warn("行程表扩展列初始化跳过，数据源不可用：{}", e.getMessage());
            return;
        }
        COLUMNS.forEach((column, ddl) -> {
            try {
                Integer exists = jdbcTemplate.queryForObject(COLUMN_CHECK_SQL, Integer.class, column);
                if (exists != null && exists > 0) {
                    return;
                }
                jdbcTemplate.execute(ddl);
                log.info("行程表新增 {} 列完成", column);
            } catch (Exception e) {
                log.warn("行程表 {} 列初始化失败：{}", column, e.getMessage());
            }
        });
    }
}
