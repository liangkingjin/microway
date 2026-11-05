package com.wtlx.infrastructure.scenic.schema;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 行程表 {@code spot_name} 列初始化（业务库 MySQL）。
 * <p>
 * 为什么需要它：实体 {@code ItineraryItems} 新增了 {@code spotName} 字段，若部署环境
 * 漏执行 DDL，MyBatis-Plus 的查询会因"列不存在"整体失败——表现是整个行程列表打不开，
 * 排查成本远高于启动时多一次 information_schema 查询。
 * <p>
 * 迁移逻辑与 {@code db/schema-itinerary-spot-mysql.sql} 完全一致（MySQL 不支持
 * {@code ADD COLUMN IF NOT EXISTS}，所以先判断再加）。失败只告警不阻断启动。
 *
 * @author 金小梁
 */
@Slf4j
@Component
@Order(22)
public class ItinerarySpotSchemaInitializer implements ApplicationRunner {

    private static final String COLUMN_CHECK_SQL =
            "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'itinerary_items' "
                    + "AND column_name = 'spot_name'";

    private static final String ADD_COLUMN_SQL =
            "ALTER TABLE itinerary_items ADD COLUMN spot_name VARCHAR(128) NULL "
                    + "COMMENT '关联景点名，用于点开景点详情' AFTER content";

    /** 主数据源（@Primary），行程表在业务 MySQL 上 */
    @Resource
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            Integer exists = jdbcTemplate.queryForObject(COLUMN_CHECK_SQL, Integer.class);
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute(ADD_COLUMN_SQL);
            log.info("行程表新增 spot_name 列完成（景点名可点开详情）");
        } catch (Exception e) {
            log.warn("行程表 spot_name 列初始化失败，景点名将无法点开详情：{}", e.getMessage());
        }
    }
}
