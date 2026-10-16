package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.ScoredMemory;

import java.util.List;

/**
 * 记忆检索路（并行检索中的一路）。
 * <p>
 * 把"怎么找"抽象成可插拔的一路，是为了让两种失效模式互相兜底：
 * <ul>
 *     <li>语义路怕 embedding 抖动、语义漂移，以及"问句里没有偏好线索"这种硬盲区
 *         （它靠画像兜底补上）；</li>
 *     <li>关键词路怕同义改写（"穷游" vs "预算紧张"），但专有名词与字面重合总能捞回来。</li>
 * </ul>
 * 两路的失效条件是正交的，因此任一路挂掉都不会导致"记忆整体消失"——
 * 这是选多路 + RRF 融合（而非单路精调）的根本原因。
 * <p>
 * 新增一路只需实现本接口并注册为 Spring Bean，{@code MemoryRetrievalService} 自动纳入融合。
 *
 * @author 金小梁
 */
public interface MemoryPath {

    /**
     * 路径名称，出现在调试输出的 pathScores 里
     */
    String name();

    /**
     * RRF 融合权重：主路权重高，兜底路权重低
     */
    double weight();

    /**
     * 执行检索
     *
     * @param subjectKey         记忆主体键
     * @param query              用户查询原文
     * @param queryVectorLiteral 查询向量字面量；向量路为空时其他路仍照常工作
     * @param topK               本路召回条数
     * @return 按本路相关性降序的命中；未命中返回空列表，<b>不得返回 null</b>
     */
    List<ScoredMemory> retrieve(String subjectKey, String query, String queryVectorLiteral, int topK);
}
