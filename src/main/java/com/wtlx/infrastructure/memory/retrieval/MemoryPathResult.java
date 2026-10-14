package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.ScoredMemory;

import java.util.List;

/**
 * 单条检索路的执行结果。
 * <p>
 * 独立成类型而不是直接传 {@code List<ScoredMemory>}，是因为融合阶段必须知道
 * "这个排名来自哪一路、该路权重多少"——RRF 的本质就是"用不同来源的排名投票"，
 * 丢掉来源信息就无法加权，也无法把 {@code pathScores} 输出到调试接口。
 *
 * @param path  路径名称
 * @param weight 该路权重
 * @param hits  该路命中（按本路相关性降序）
 * @author 金小梁
 */
public record MemoryPathResult(String path, double weight, List<ScoredMemory> hits) {

    public static MemoryPathResult empty(MemoryPath path) {
        return new MemoryPathResult(path.name(), path.weight(), List.of());
    }
}
