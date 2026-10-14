package com.wtlx.domain.entity.memory;

/**
 * 近重复记忆对（冗余合并清理的输入）。
 * <p>
 * 由 SQL 自连接按向量相似度找出"表述不同、事实相同"的两条生效记忆，
 * 并在 SQL 侧就定好了保留方（置信度更高、或同样置信但表述更完整的那条），
 * 避免把 1024 维向量拉回 JVM 再做聚类。
 * <p>
 * Java 侧仍需按相似度降序"贪心 + 实时校验状态"地处理：
 * 若 {@code keep} 自己已被前一对判为冗余而被取代，这一对整体跳过，
 * 否则会出现"A 保留、B 保留、B 又被取代"的自相矛盾状态。
 *
 * @param keepMemoryId 应保留的记忆对外标识
 * @param dropMemoryId 应被取代的记忆对外标识
 * @param similarity   两者的余弦相似度
 * @author 金小梁
 */
public record DuplicatePair(String keepMemoryId, String dropMemoryId, double similarity) {
}
