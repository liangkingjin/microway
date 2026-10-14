package com.wtlx.infrastructure.memory.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.support.MemoryConstants;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 检索路 D · 稳定层路（与查询无关）。
 * <p>
 * 按 {@code 置信度 × 重要度} 取长期稳定的高价值记忆，完全不看用户这一轮问什么。
 * <p>
 * <b>这条路是"跨会话稳定复用"的落地保证</b>。其余三路都以"与查询相关"为前提，
 * 于是会出现一个反直觉的问题：用户新开会话问"帮我规划成都三天"，如果问句里没有
 * 任何偏好线索，三条查询路都可能召回不到"饮食忌辣"这条老记忆——它明明最有价值，
 * 只是因为"这轮没提到"而消失。稳定层路就是用来堵这个洞的：
 * 高置信、长期有效的画像始终进入上下文，把"个性化"从"碰运气"变成"确定性"。
 * <p>
 * 因为与查询无关，它是整个链路里<b>唯一可安全缓存</b>的一路：结果只随记忆写入变化，
 * 因此由 {@code MemoryMergeService} 在写入后主动失效缓存键，并附加 TTL 兜底。
 * 缓存同时缓存"空结果"，避免无记忆的新用户每轮都穿透到数据库。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class StableMemoryPath implements MemoryPath {

    private static final String PATH_NAME = "stable";

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String name() {
        return PATH_NAME;
    }

    @Override
    public double weight() {
        return memoryProperties.getStableWeight();
    }

    @Override
    public List<ScoredMemory> retrieve(String subjectKey, String query, String queryVectorLiteral, int topK) {
        String cacheKey = MemoryConstants.CACHE_STABLE_PREFIX + subjectKey;

        // 1. 命中缓存（含"空结果"缓存，防穿透）
        List<ScoredMemory> cached = readCache(cacheKey);
        if (cached != null) {
            log.debug("稳定层路命中缓存，{} 条", cached.size());
            return cached;
        }

        // 2. 回源
        List<ScoredMemory> hits = userMemoryMapper.searchStable(subjectKey, topK);

        // 3. 写缓存
        writeCache(cacheKey, hits);
        return hits;
    }

    /**
     * 读缓存
     *
     * @return null 表示未命中（需回源）；空列表表示命中"确实没有稳定记忆"
     */
    private List<ScoredMemory> readCache(String cacheKey) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return null;
            }
            if (cached.isBlank()) {
                return List.of();
            }
            List<UserMemory> memories = objectMapper.readValue(cached, new TypeReference<List<UserMemory>>() {
            });
            return memories.stream()
                    .map(memory -> new ScoredMemory(memory, memory.getConfidence() * memory.getImportance()))
                    .toList();
        } catch (Exception e) {
            // 缓存损坏（例如实体新增字段后反序列化失败）：当作未命中，回源并覆盖
            log.debug("稳定层缓存读取失败，回源查询: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, List<ScoredMemory> hits) {
        try {
            String payload = hits.isEmpty()
                    ? ""
                    : objectMapper.writeValueAsString(hits.stream().map(ScoredMemory::memory).toList());
            stringRedisTemplate.opsForValue().set(cacheKey, payload,
                    memoryProperties.getContextCacheTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("稳定层缓存写入失败（不影响本次检索）: {}", e.getMessage());
        }
    }
}
