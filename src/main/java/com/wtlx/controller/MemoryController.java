package com.wtlx.controller;

import com.wtlx.domain.entity.conversation.ConversationMessage;
import com.wtlx.domain.entity.conversation.ConversationSummary;
import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.memory.MemoryHit;
import com.wtlx.domain.entity.memory.MemoryOverviewVO;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.infrastructure.memory.extraction.MemoryExtractionService;
import com.wtlx.infrastructure.memory.retrieval.MemoryRetrievalService;
import com.wtlx.infrastructure.memory.schema.MemorySchemaInitializer;
import com.wtlx.infrastructure.memory.support.MemoryPrincipal;
import com.wtlx.infrastructure.memory.support.MemorySubjectResolver;
import com.wtlx.repository.mapper.ConversationMessageMapper;
import com.wtlx.repository.mapper.ConversationSummaryMapper;
import com.wtlx.repository.mapper.UserMemoryMapper;
import com.wtlx.service.ConversationMemoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆管理接口：观测、检索调试、手动抽取。
 * <p>
 * 与 {@code RagAdminController} 同族，映射在 {@code /ai/**} 下。
 * <b>所有接口要求登录</b>（{@code LoginInterceptor} 已在 {@code /ai/**} 生效），
 * 记忆主体统一取当前登录用户，不支持指定他人——越权读写他人画像比接口报错严重得多，
 * 因此根本没有"换一个 userId 查查"这个入口。
 *
 * @author 金小梁
 */
@Slf4j
@Api(tags = "用户长期记忆管理接口")
@RestController
@RequestMapping("/ai/memory")
public class MemoryController {

    /**
     * 管理接口单次返回条数上限
     */
    private static final int MAX_LIMIT = 200;

    /**
     * 概览里回溯的最近消息条数
     */
    private static final int OVERVIEW_RECENT_MESSAGES = 6;

    @Resource
    private MemorySubjectResolver memorySubjectResolver;

    @Resource
    private MemoryRetrievalService memoryRetrievalService;

    @Resource
    private MemoryExtractionService memoryExtractionService;

    @Resource
    private MemorySchemaInitializer memorySchemaInitializer;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private ConversationSummaryMapper conversationSummaryMapper;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @ApiOperation("记忆全景：短期记忆与长期记忆对照")
    @GetMapping("/overview")
    public Result overview(@RequestParam(required = false) String conversationId,
                           @RequestParam(required = false) String query) {
        MemoryPrincipal principal = memorySubjectResolver.resolve();

        MemoryOverviewVO overview = new MemoryOverviewVO();
        overview.setSubjectKey(principal.subjectKey());

        // 短期记忆：作用域是会话，换 threadId 就变
        if (StringUtils.hasText(conversationId)) {
            overview.setSession(buildSessionScope(conversationId));
        }

        // 长期记忆：作用域是用户，换 threadId 不变
        MemoryOverviewVO.LongTermScope longTerm = new MemoryOverviewVO.LongTermScope();
        longTerm.setActiveCount(userMemoryMapper.countActive(principal.subjectKey()));
        longTerm.setCountByType(userMemoryMapper.countActiveByType(principal.subjectKey()));
        if (StringUtils.hasText(query)) {
            List<MemoryHit> hits = memoryRetrievalService.retrieve(principal.subjectKey(), query);
            overview.setHits(hits);
            longTerm.setHitCount(hits.size());
        }
        overview.setLongTerm(longTerm);
        return Result.ok(overview);
    }

    @ApiOperation("列举当前用户的长期记忆")
    @GetMapping("/list")
    public Result list(@RequestParam(required = false) String type,
                       @RequestParam(required = false, defaultValue = "ACTIVE") String status,
                       @RequestParam(required = false) Integer limit) {
        MemoryPrincipal principal = memorySubjectResolver.resolve();
        List<UserMemory> memories = userMemoryMapper.listBySubject(
                principal.subjectKey(), type, status, clampLimit(limit, 50));
        return Result.ok(memories);
    }

    @ApiOperation("向量检索调试（含命中相似度）")
    @GetMapping("/search")
    public Result search(@RequestParam String query,
                         @RequestParam(required = false) Integer topN) {
        MemoryPrincipal principal = memorySubjectResolver.resolve();
        List<MemoryHit> hits = memoryRetrievalService.retrieve(
                principal.subjectKey(), query, clampLimit(topN, 8));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectKey", principal.subjectKey());
        payload.put("query", query);
        payload.put("hitCount", hits.size());
        payload.put("hits", hits);
        return Result.ok(payload);
    }

    @ApiOperation("手动触发记忆抽取（调试与补偿）")
    @PostMapping("/extract")
    public Result extract(@RequestParam String conversationId) {
        MemoryPrincipal principal = memorySubjectResolver.resolve();
        int applied = memoryExtractionService.extractNow(principal.subjectKey(), conversationId);
        return Result.ok("抽取完成，落库 " + applied + " 条记忆");
    }

    @ApiOperation("删除单条记忆（软删）")
    @DeleteMapping("/{memoryId}")
    public Result deleteOne(@PathVariable String memoryId) {
        MemoryPrincipal principal = memorySubjectResolver.resolve();
        int rows = userMemoryMapper.markDeleted(principal.subjectKey(), memoryId);
        return rows > 0 ? Result.ok("已删除") : Result.fail("未找到该记忆或已被删除");
    }

    @ApiOperation("清空当前用户的全部长期记忆（物理删除，含历史行）")
    @DeleteMapping
    public Result deleteSubject() {
        MemoryPrincipal principal = memorySubjectResolver.resolve();
        int rows = userMemoryMapper.deleteBySubject(principal.subjectKey());
        return Result.ok("已清空 " + rows + " 条记忆");
    }

    @ApiOperation("补建记忆相关表结构（PG 恢复后重试）")
    @PostMapping("/schema")
    public Result ensureSchema() {
        int failures = memorySchemaInitializer.ensureSchema();
        return failures == 0 ? Result.ok("表结构就绪") : Result.fail("存在 " + failures + " 条 DDL 失败，请查看日志");
    }

    @ApiOperation("全局记忆类型分布（运维观测）")
    @GetMapping("/stats")
    public Result stats() {
        return Result.ok(userMemoryMapper.countAllActiveByType());
    }

    /**
     * 组装短期记忆视图
     */
    private MemoryOverviewVO.SessionScope buildSessionScope(String conversationId) {
        MemoryOverviewVO.SessionScope scope = new MemoryOverviewVO.SessionScope();
        scope.setConversationId(conversationId);
        scope.setMessageCount(conversationMessageMapper.countByConversationId(conversationId));
        scope.setUserTurns(conversationMessageMapper.countUserMessages(conversationId));
        ConversationSummary summary = conversationSummaryMapper.findByConversationId(conversationId);
        if (summary != null) {
            scope.setSummary(summary.getSummary());
            scope.setSummaryMessageCount(summary.getMessageCount());
        }
        List<ConversationMessage> recent =
                conversationMemoryService.loadRecentMessages(conversationId, OVERVIEW_RECENT_MESSAGES);
        List<MemoryOverviewVO.MessagePreview> previews = new ArrayList<>(recent.size());
        for (ConversationMessage message : recent) {
            MemoryOverviewVO.MessagePreview preview = new MemoryOverviewVO.MessagePreview();
            preview.setRole(message.getRole() == null ? null : message.getRole().getCode());
            preview.setContent(abbreviate(message.getContent()));
            preview.setTime(message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
            previews.add(preview);
        }
        scope.setRecentMessages(previews);
        return scope;
    }

    private int clampLimit(Integer limit, int defaultValue) {
        int value = limit == null ? defaultValue : limit;
        return Math.max(1, Math.min(MAX_LIMIT, value));
    }

    private String abbreviate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > 120 ? content.substring(0, 120) + "..." : content;
    }
}
