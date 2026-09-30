package com.wtlx.controller;

import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.domain.entity.RagSourceIndex;
import com.wtlx.domain.entity.dto.Result;
import com.wtlx.repository.mapper.RagSourceIndexMapper;
import com.wtlx.infrastructure.rag.retriever.RagHit;
import com.wtlx.infrastructure.rag.loader.RagIndexService;
import com.wtlx.infrastructure.rag.config.RagProperties;
import com.wtlx.infrastructure.rag.loader.RagRebuildService;
import com.wtlx.infrastructure.rag.retriever.RagSearchService;
import com.wtlx.infrastructure.rag.loader.RebuildStat;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * RAG 管理接口：检索调试、单个来源重建、全量回填、索引状态查询。
 *
 * @author 金小梁
 */
@Slf4j
@Api(tags = "RAG 检索管理接口")
@RestController
@RequestMapping("/ai/rag")
public class RagAdminController {

    @Resource
    private RagSearchService ragSearchService;

    @Resource
    private RagIndexService ragIndexService;

    @Resource
    private RagRebuildService ragRebuildService;

    @Resource
    private RagSourceIndexMapper indexMapper;

    @Resource
    private RagProperties ragProperties;

    @ApiOperation("相似度检索（调试用）")
    @GetMapping("/search")
    public Result search(@RequestParam String query,
                         @RequestParam(required = false) Integer topK,
                         @RequestParam(required = false) Double threshold,
                         @RequestParam(required = false) String docTypes) {
        List<String> types = StringUtils.hasText(docTypes)
                ? Arrays.stream(docTypes.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of(RagDocType.BLOG, RagDocType.GUIDE);
        List<RagHit> hits = ragSearchService.search(query,
                topK == null ? ragProperties.getSearchTopK() : topK,
                threshold == null ? ragProperties.getSearchThreshold() : threshold,
                types);
        return Result.ok(hits);
    }

    @ApiOperation("重建单个来源的向量索引（失败补偿）")
    @PostMapping("/reindex/{docType}/{sourceId}")
    public Result reindex(@PathVariable String docType, @PathVariable Long sourceId) {
        RagIndexService.RagIndexResult result = ragIndexService.index(docType, sourceId);
        return Result.ok(result.getStatus().name() + " / chunks=" + result.getChunkCount());
    }

    @ApiOperation("删除单个来源的向量索引")
    @PostMapping("/remove/{docType}/{sourceId}")
    public Result remove(@PathVariable String docType, @PathVariable Long sourceId) {
        ragIndexService.remove(docType, sourceId);
        return Result.ok("已删除");
    }

    @ApiOperation("全量回填（BLOG / GUIDE）")
    @PostMapping("/rebuild/{docType}")
    public Result rebuild(@PathVariable String docType) {
        RebuildStat stat = ragRebuildService.rebuildAll(docType);
        return Result.ok(stat);
    }

    @ApiOperation("回填缺失索引（BLOG / GUIDE）")
    @PostMapping("/rebuild-missing/{docType}")
    public Result rebuildMissing(@PathVariable String docType) {
        RebuildStat stat = ragRebuildService.rebuildMissing(docType);
        return Result.ok(stat);
    }

    @ApiOperation("查询来源索引状态")
    @GetMapping("/index/status")
    public Result status(@RequestParam String docType, @RequestParam Long sourceId) {
        RagSourceIndex record = indexMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RagSourceIndex>()
                        .eq(RagSourceIndex::getDocType, docType)
                        .eq(RagSourceIndex::getSourceId, sourceId)
                        .last("limit 1"));
        return Result.ok(record);
    }
}
