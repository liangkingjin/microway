package com.wtlx.infrastructure.rag.loader;

import com.wtlx.infrastructure.rag.config.RagProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.domain.entity.Blog;
import com.wtlx.domain.entity.Guides;
import com.wtlx.domain.entity.RagSourceIndex;
import com.wtlx.repository.mapper.BlogMapper;
import com.wtlx.repository.mapper.GuidesMapper;
import com.wtlx.repository.mapper.RagSourceIndexMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RAG 全量回填服务：把历史存量数据补进向量库。
 * <p>
 * 两类场景：
 * <ul>
 *     <li>{@link #rebuildMissing(String)}：只补"索引表里没有"的来源，用于启动自检；</li>
 *     <li>{@link #rebuildAll(String)}：强制全量重算，用于数据修复（受索引表指纹保护，
 *         内容未变的仍会跳过，不会浪费 embedding 配额）。</li>
 * </ul>
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class RagRebuildService {

    /**
     * 启动自检延迟：等待 Web 容器与连接池就绪
     */
    private static final long STARTUP_DELAY_MS = 15000L;

    @Resource
    private RagIndexService ragIndexService;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private GuidesMapper guidesMapper;

    @Resource
    private RagSourceIndexMapper indexMapper;

    /**
     * 启动时按需回填缺失索引（受 {@code trip-agent.rag.rebuild-on-startup} 控制）
     */
    @PostConstruct
    public void startupRebuild() {
        if (!ragProperties.isEnabled() || !ragProperties.isRebuildOnStartup()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(STARTUP_DELAY_MS);
                log.info("RAG 启动回填开始");
                log.info("RAG 启动回填完成（游记）: {}", rebuildMissing(RagDocType.BLOG));
                log.info("RAG 启动回填完成（攻略）: {}", rebuildMissing(RagDocType.GUIDE));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("RAG 启动回填异常", e);
            }
        });
    }

    /**
     * 回填尚未建立索引的来源
     *
     * @param docType 来源类型
     * @return 统计
     */
    public RebuildStat rebuildMissing(String docType) {
        List<Long> all = listAllSourceIds(docType);
        if (all.isEmpty()) {
            return new RebuildStat();
        }
        Set<Long> indexed = new HashSet<>(listIndexedSourceIds(docType));
        List<Long> missing = all.stream().filter(id -> !indexed.contains(id)).toList();
        RebuildStat stat = ragIndexService.indexBatch(docType, missing);
        stat.setTotal(missing.size());
        return stat;
    }

    /**
     * 全量重建（内容未变的会被指纹短路跳过）
     *
     * @param docType 来源类型
     * @return 统计
     */
    public RebuildStat rebuildAll(String docType) {
        List<Long> all = listAllSourceIds(docType);
        RebuildStat stat = ragIndexService.indexBatch(docType, all);
        stat.setTotal(all.size());
        return stat;
    }

    private List<Long> listAllSourceIds(String docType) {
        if (RagDocType.BLOG.equals(docType)) {
            return blogMapper.selectList(new LambdaQueryWrapper<Blog>()
                            .select(Blog::getId))
                    .stream().map(Blog::getId).collect(Collectors.toList());
        }
        if (RagDocType.GUIDE.equals(docType)) {
            return guidesMapper.selectList(new LambdaQueryWrapper<Guides>()
                            .select(Guides::getId))
                    .stream().map(guide -> guide.getId().longValue()).collect(Collectors.toList());
        }
        return List.of();
    }

    private List<Long> listIndexedSourceIds(String docType) {
        if (!StringUtils.hasText(docType)) {
            return List.of();
        }
        return indexMapper.selectList(new LambdaQueryWrapper<RagSourceIndex>()
                        .select(RagSourceIndex::getSourceId)
                        .eq(RagSourceIndex::getDocType, docType))
                .stream().map(RagSourceIndex::getSourceId).collect(Collectors.toList());
    }
}
