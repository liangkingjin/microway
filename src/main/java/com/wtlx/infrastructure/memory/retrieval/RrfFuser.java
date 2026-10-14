package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.MemoryHit;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.domain.entity.memory.enums.MemoryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion，倒数排名融合）融合器。
 * <p>
 * <b>为什么不能直接加权求和各路得分</b>：四路的原始分量纲完全不同——
 * 向量路是 0~1 的余弦相似度、关键词路是 0~N 的命中词数、稳定路是
 * {@code 置信度 × 重要度}。把它们相加，等于让"命中 3 个关键词"压过"余弦相似度 0.95"，
 * 而这两者在语义上根本不可比。归一化也救不了：各路分数的分布形状不同，
 * 线性归一化会把关键词路的噪声放到最大。
 * <p>
 * RRF 绕开量纲问题：只取每路的<b>排名</b>，用 {@code weight / (k + rank)} 累加。
 * 一条记忆若被多路同时召回，就会在多个排名位置上各拿一次分，
 * 从而天然获得"交叉验证"的收益——这正是多路检索想要的效果：
 * <b>不是某一路说得算，而是多数路都指向它</b>。
 * <p>
 * 最终分数会按最大值归一化到 (0,1]，仅为便于阅读与阈值观察；
 * 它没有绝对物理含义，不要拿它跟向量相似度做比较。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class RrfFuser {

    /**
     * 融合各路结果
     *
     * @param results 各路结果
     * @param k       RRF 平滑常数，越大则各路排名差异被压得越平
     * @param topN    最终返回条数
     * @return 融合后的命中，按融合分降序；无命中返回空列表
     */
    public List<MemoryHit> fuse(List<MemoryPathResult> results, int k, int topN) {
        Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        int smooth = Math.max(1, k);

        for (MemoryPathResult result : results) {
            if (result == null || result.hits() == null || result.hits().isEmpty()) {
                continue;
            }
            int rank = 0;
            for (ScoredMemory scored : result.hits()) {
                rank++;
                UserMemory memory = scored.memory();
                if (memory == null || memory.getMemoryId() == null) {
                    continue;
                }
                Accumulator accumulator = accumulators.computeIfAbsent(
                        memory.getMemoryId(), id -> new Accumulator(memory));
                accumulator.score += result.weight() / (smooth + rank);
                // 记录本路原始分：调试接口靠它回答"这条为什么被召回"
                accumulator.pathScores.put(result.path(), scored.pathScore());
            }
        }

        if (accumulators.isEmpty()) {
            return List.of();
        }

        List<Accumulator> ranked = new ArrayList<>(accumulators.values());
        ranked.sort(Comparator.comparingDouble((Accumulator a) -> a.score).reversed());
        if (ranked.size() > topN) {
            ranked = ranked.subList(0, topN);
        }

        double max = ranked.get(0).score;
        List<MemoryHit> hits = new ArrayList<>(ranked.size());
        for (Accumulator accumulator : ranked) {
            UserMemory memory = accumulator.memory;
            hits.add(MemoryHit.builder()
                    .memoryId(memory.getMemoryId())
                    .type(MemoryType.fromCode(memory.getMemoryType()))
                    .key(memory.getMemoryKey())
                    .content(memory.getContent())
                    .structured(memory.getStructured())
                    .confidence(memory.getConfidence())
                    .importance(memory.getImportance())
                    .validTo(memory.getValidTo())
                    .updatedAt(memory.getUpdatedAt())
                    .score(max > 0 ? accumulator.score / max : 0D)
                    .pathScores(accumulator.pathScores)
                    .build());
        }
        log.debug("RRF 融合完成，{} 路输入 → {} 条输出", results.size(), hits.size());
        return hits;
    }

    /**
     * 融合累加器：把"多路命中同一条记忆"的分数与来源汇总到一处
     */
    private static final class Accumulator {

        private final UserMemory memory;

        private double score;

        private final Map<String, Double> pathScores = new LinkedHashMap<>();

        private Accumulator(UserMemory memory) {
            this.memory = memory;
        }
    }
}
