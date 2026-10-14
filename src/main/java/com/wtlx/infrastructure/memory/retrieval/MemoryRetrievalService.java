package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.MemoryHit;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.embedding.MemoryEmbeddingService;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 长期记忆多路并行检索服务。
 * <p>
 * <b>为什么必须并行</b>：四路检索串行执行时，总延迟是四路之和（每路一次 PG 查询，
 * 向量路还带 HNSW 扫描）。而它就挂在模型调用前的关键路径上——每一次对话的首字延迟
 * 都会被它直接放大。并行后延迟等于最慢的一路。
 * <p>
 * <b>为什么每路必须独立超时</b>：任一路慢（例如向量路在记忆量大时 HNSW 扫描变慢、
 * 或 Redis 抖动）都不能拖垮对话。故每路 {@code orTimeout} 后降级为空结果参与融合，
 * 由剩余各路继续支撑召回。这是"记忆是增强，不是依赖"的直接体现：
 * 记忆服务全挂时，Agent 仍应能正常回答，只是不再个性化。
 * <p>
 * 查询向量在此处<b>统一计算一次</b>后传给各路，避免各路各算一次 embedding（多花 3 倍费用）。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class MemoryRetrievalService {

    /**
     * 所有检索路（Spring 自动注入全部 {@link MemoryPath} 实现，新增一路无需改本类）
     */
    private final List<MemoryPath> paths;

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private MemoryEmbeddingService memoryEmbeddingService;

    @Resource
    private RrfFuser rrfFuser;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource(name = "memoryRetrievalExecutor")
    private Executor memoryRetrievalExecutor;

    public MemoryRetrievalService(List<MemoryPath> paths) {
        this.paths = paths;
    }

    /**
     * 按默认配置检索
     */
    public List<MemoryHit> retrieve(String subjectKey, String query) {
        return retrieve(subjectKey, query, memoryProperties.getRetrievalTopN());
    }

    /**
     * 多路并行检索并融合
     *
     * @param subjectKey 记忆主体键
     * @param query      用户查询
     * @param topN       返回条数
     * @return 融合后的命中，按融合分降序；主体缺失、开关关闭或无命中时返回空列表
     */
    public List<MemoryHit> retrieve(String subjectKey, String query, int topN) {
        if (!memoryProperties.isEnabled() || !StringUtils.hasText(subjectKey)) {
            return List.of();
        }
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        // 1. 查询向量统一计算一次，四路共用
        String queryVectorLiteral = memoryEmbeddingService.embedToLiteral(query);

        // 2. 四路并行（各自独立超时 + 独立降级）
        List<CompletableFuture<MemoryPathResult>> futures = new ArrayList<>(paths.size());
        for (MemoryPath path : paths) {
            futures.add(CompletableFuture
                    .supplyAsync(() -> runPath(path, subjectKey, query, queryVectorLiteral), memoryRetrievalExecutor)
                    .orTimeout(memoryProperties.getPathTimeoutMs(), TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("记忆检索路 {} 失败/超时，本路降级为空: {}", path.name(), rootMessage(ex));
                        return MemoryPathResult.empty(path);
                    }));
        }

        List<MemoryPathResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            // exceptionally 已兜底，join 不会抛；仍包一层以便单路异常不连累整体
            try {
                results.add(futures.get(i).join());
            } catch (Exception e) {
                results.add(MemoryPathResult.empty(paths.get(i)));
            }
        }

        // 3. RRF 融合
        List<MemoryHit> hits = rrfFuser.fuse(results, memoryProperties.getRrfK(), topN);
        if (hits.isEmpty()) {
            return hits;
        }

        // 4. 命中统计异步回写（不阻塞对话）
        trackHitStats(hits);
        log.info("长期记忆检索完成, subject: {}, 命中 {} 条, 各路口径: {}",
                subjectKey, hits.size(), describe(results));
        return hits;
    }

    /**
     * 执行单路检索并把异常收敛为空结果（路内失败不抛出）
     */
    private MemoryPathResult runPath(MemoryPath path, String subjectKey, String query, String queryVectorLiteral) {
        try {
            List<ScoredMemory> hits =
                    path.retrieve(subjectKey, query, queryVectorLiteral, resolveTopK(path.name()));
            return new MemoryPathResult(path.name(), path.weight(), hits == null ? List.of() : hits);
        } catch (Exception e) {
            log.warn("记忆检索路 {} 执行异常: {}", path.name(), e.getMessage());
            return MemoryPathResult.empty(path);
        }
    }

    /**
     * 汇总各路命中数，便于观察"哪条路在起作用"
     */
    private String describe(List<MemoryPathResult> results) {
        StringBuilder sb = new StringBuilder();
        for (MemoryPathResult result : results) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(result.path()).append('=').append(result.hits().size());
        }
        return sb.toString();
    }

    /**
     * 异步回写 hit_count / last_hit_at：复用强度是清理阶段的判断依据，但让它阻塞对话毫无必要
     */
    private void trackHitStats(List<MemoryHit> hits) {
        if (!memoryProperties.isTrackHitStats()) {
            return;
        }
        List<String> ids = hits.stream().map(MemoryHit::getMemoryId).toList();
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    userMemoryMapper.bumpHitStats(ids);
                } catch (Exception e) {
                    log.debug("记忆命中统计回写失败: {}", e.getMessage());
                }
            }, memoryRetrievalExecutor);
        } catch (Exception e) {
            log.debug("记忆命中统计任务提交失败: {}", e.getMessage());
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    /**
     * 各路召回条数解析：按路名取对应配置，未登记的路退回向量路条数。
     * <p>
     * 集中一处而不是散在各路的 if-else 里：新增一路时只需在这里补一行，
     * 忘记补也不会导致启动失败，只是用默认条数。
     */
    private int resolveTopK(String pathName) {
        return switch (pathName) {
            case "vector" -> memoryProperties.getVectorTopK();
            case "slot" -> memoryProperties.getSlotTopK();
            case "keyword" -> memoryProperties.getKeywordTopK();
            case "stable" -> memoryProperties.getStableTopK();
            default -> memoryProperties.getVectorTopK();
        };
    }
}
