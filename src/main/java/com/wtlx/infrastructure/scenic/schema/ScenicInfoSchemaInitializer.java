package com.wtlx.infrastructure.scenic.schema;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 景点 AI 详情表结构初始化器。
 * <p>
 * 与 {@code MemorySchemaInitializer} 同一套模式：无 Flyway/Liquibase，DDL 唯一来源是
 * {@value #DDL_SCRIPT}，启动期幂等执行；失败记日志不阻断启动（PG 不可用时仅景点
 * AI 详情不可用，主业务不受影响）。
 *
 * @author 金小梁
 */
@Slf4j
@Component
@Order(21)
public class ScenicInfoSchemaInitializer implements ApplicationRunner {

    private static final String DDL_SCRIPT = "db/schema-scenic-spot.sql";

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate aiJdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<String> statements = loadStatements();
        List<String> failures = new ArrayList<>();
        for (String ddl : statements) {
            try {
                aiJdbcTemplate.execute(ddl);
            } catch (Exception e) {
                failures.add(ddl + " → " + e.getMessage());
            }
        }
        if (failures.isEmpty()) {
            log.info("景点 AI 详情表结构初始化完成，共 {} 条 DDL", statements.size());
        } else {
            log.warn("景点 AI 详情表部分 DDL 失败（共{}条）：{}", failures.size(), failures);
        }
    }

    private List<String> loadStatements() {
        try (var in = new ClassPathResource(DDL_SCRIPT).getInputStream()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> statements = new ArrayList<>();
            for (String s : sql.split(";")) {
                // 先剔除注释行再判空：脚本开头的 -- 注释块不能把整段 CREATE TABLE 一起吞掉
                String stripped = s.replaceAll("(?m)^--.*$", "").strip();
                if (!stripped.isEmpty()) {
                    statements.add(stripped);
                }
            }
            return statements;
        } catch (Exception e) {
            log.error("景点 AI 详情 DDL 脚本读取失败：{}", DDL_SCRIPT, e);
            return List.of();
        }
    }
}
