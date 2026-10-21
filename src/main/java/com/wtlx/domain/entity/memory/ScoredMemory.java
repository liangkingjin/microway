package com.wtlx.domain.entity.memory;

/**
 * 带分数的记忆（单条向量检索的原始命中）。
 * <p>
 * 检索目前为单路向量召回，{@code pathScore} 即该条记忆与查询向量的余弦相似度，
 * 量纲 0~1，直接用于结果排序与上下文注入的冷热分层判断。
 *
 * @param memory    记忆本体
 * @param pathScore 向量余弦相似度
 * @author 金小梁
 */
public record ScoredMemory(UserMemory memory, double pathScore) {
}
