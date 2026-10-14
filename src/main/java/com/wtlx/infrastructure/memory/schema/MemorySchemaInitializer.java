package com.wtlx.infrastructure.memory.schema;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆表结构初始化器。
 * <p>
 * 项目没有 Flyway/Liquibase，DDL 一律是手工可执行的幂等脚本。这里在启动期把
 * {@value #DDL_SCRIPT} 再跑一遍，理由与 PGVector 的 {@code initialize-schema: true} 一致：
 * 新增能力不应该要求部署方记得额外执行一次脚本——漏执行的表现是"记忆静默不生效"，
 * 排查成本远高于启动时多几条 DDL。
 * <p>
 * <b>DDL 只有一份</b>：语句存放在 {@value #DDL_SCRIPT} 资源文件中，本类负责读取并按分号切分，
 * 不再把同样的建表语句在 Java 常量数组里抄一遍。此前两处并存（Java 常量 + SQL 脚本），
 * 靠"保持同步"的注释维系一致，是典型的漂移隐患——改了一处、忘了另一处，
 * 就会出现"代码里看着有这张表、数据库里没有"的静默故障。
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
     * 长期记忆 DDL 脚本（classpath）。唯一来源，同时供手工执行
     */
    private static final String DDL_SCRIPT = "db/schema-user-memory.sql";

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate aiJdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureSchema();
    }

    /**
     * 执行幂等 DDL。
     *
     * @return 执行失败（含脚本读取失败）的语句数量；0 表示全部成功
     */
    public int ensureSchema() {
        List<String> statements;
        try {
            statements = loadStatements();
        } catch (Exception e) {
            // 脚本读不到属于部署错误（资源未打包），必须显式报失败而不是静默返回 0
            log.error("长期记忆 DDL 脚本读取失败：{}，长期记忆相关功能将不可用", DDL_SCRIPT, e);
            return 1;
        }

        List<String> failures = new ArrayList<>();
        for (String ddl : statements) {
            try {
                aiJdbcTemplate.execute(ddl);
            } catch (Exception e) {
                // 单条失败不中断：CREATE EXTENSION 需要超级用户，普通账号下失败属预期，
                // 只要扩展已由 DBA 建好，后续建表语句仍能正常执行
                failures.add(firstLine(ddl) + " → " + e.getMessage());
            }
        }
        if (failures.isEmpty()) {
            log.info("用户长期记忆表结构初始化完成，共 {} 条 DDL", statements.size());
            return 0;
        }
        log.warn("用户长期记忆表结构初始化存在 {} 条失败（不影响启动，可在 PG 恢复后重试）；"
                        + "若长期记忆相关接口报错，请手工执行 resources/{}：\n  {}",
                failures.size(), DDL_SCRIPT, String.join("\n  ", failures));
        return failures.size();
    }

    /**
     * 读取 DDL 脚本并切分为单条语句。
     * <p>
     * 切分依据是"行尾分号"：本脚本只含 DDL，不含 PL/pgSQL 函数体，
     * 也没有把分号写进字符串字面量的场景，因此无需引入 SQL 解析器；
     * {@code --} 注释行与空行直接跳过。
     */
    private List<String> loadStatements() throws IOException {
        ClassPathResource resource = new ClassPathResource(DDL_SCRIPT);
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(trimmed).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().trim();
                statements.add(statement.substring(0, statement.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            // 兜底：最后一条忘了写分号也照常执行，而不是静默丢弃
            statements.add(current.toString().trim());
        }
        return statements;
    }

    private String firstLine(String ddl) {
        String trimmed = ddl.trim();
        int idx = trimmed.indexOf('\n');
        return idx > 0 ? trimmed.substring(0, idx) : trimmed;
    }
}
