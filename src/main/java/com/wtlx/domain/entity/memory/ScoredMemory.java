package com.wtlx.domain.entity.memory;

/**
 * 带分数的记忆（单条检索路的原始命中）。
 * <p>
 * 每条检索路对自己的命中给出各自的原始分（向量路是余弦相似度、关键词路是命中词数、
 * 稳定路是 置信度×重要度），量纲互不相同、不可直接相加——因此它只用于调试观察与路内排序，
 * 跨路融合一律走 RRF（只用排名，不用原始分），见 {@code RrfFuser}。
 *
 * @param memory    记忆本体
 * @param pathScore 该路的原始得分
 * @author 金小梁
 */
public record ScoredMemory(UserMemory memory, double pathScore) {
}
