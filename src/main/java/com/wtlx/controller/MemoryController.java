package com.wtlx.controller;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.service.UserMemoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户长期记忆管理接口：观测、检索调试、手动抽取。
 * <p>
 * 与 {@code RagAdminController} 同族，映射在 {@code /ai/**} 下，<b>所有接口要求登录</b>。
 * 本类只负责参数校验与结果包装，业务逻辑集中在 {@link UserMemoryService}；
 * 记忆作用域统一取当前登录用户，不提供"指定他人"入口——越权读写他人画像比接口报错严重得多。
 *
 * @author 金小梁
 */
@Api(tags = "用户长期记忆管理接口")
@RestController
@RequestMapping("/ai/memory")
public class MemoryController {

    /**
     * 管理接口单次返回条数上限
     */
    private static final int MAX_LIMIT = 200;

    @Resource
    private UserMemoryService userMemoryService;

    @ApiOperation("记忆全景：短期记忆与长期记忆对照")
    @GetMapping("/overview")
    public Result overview(@RequestParam(required = false) String conversationId,
                           @RequestParam(required = false) String query) {
        return Result.ok(userMemoryService.overview(conversationId, query));
    }

    @ApiOperation("列举当前用户的长期记忆")
    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String type,
                       @RequestParam(required = false, defaultValue = "ACTIVE") String status,
                       @RequestParam(required = false) Integer limit) {
        return Result.ok(userMemoryService.list(type, status, clampLimit(limit, 50)));
    }

    @ApiOperation("向量检索调试（含命中相似度）")
    @GetMapping("/search")
    public Result search(@RequestParam String query,
                         @RequestParam(required = false) Integer topN) {
        return Result.ok(userMemoryService.search(query, clampLimit(topN, 8)));
    }

    @ApiOperation("删除单条记忆（软删）")
    @DeleteMapping("/{memoryId}")
    public Result deleteOne(@PathVariable String memoryId) {
        return userMemoryService.deleteOne(memoryId)
                ? Result.ok("已删除")
                : Result.fail("未找到该记忆或已被删除");
    }

    @ApiOperation("清空当前用户的全部长期记忆（物理删除，含历史行）")
    @DeleteMapping
    public Result deleteSubject() {
        return Result.ok("已清空 " + userMemoryService.deleteAll() + " 条记忆");
    }

    @ApiOperation("补建记忆相关表结构（PG 恢复后重试）")
    @PostMapping("/schema")
    public Result ensureSchema() {
        int failures = userMemoryService.ensureSchema();
        return failures == 0 ? Result.ok("表结构就绪") : Result.fail("存在 " + failures + " 条 DDL 失败，请查看日志");
    }

    @ApiOperation("全局记忆类型分布（运维观测）")
    @GetMapping("/stats")
    public Result stats() {
        return Result.ok(userMemoryService.stats());
    }

    /**
     * 参数校验：把外部传入的 limit 收敛到 [1, MAX_LIMIT]
     */
    private int clampLimit(Integer limit, int defaultValue) {
        int value = limit == null ? defaultValue : limit;
        return Math.max(1, Math.min(MAX_LIMIT, value));
    }
}
