package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.domain.entity.memory.UserMemory;
import com.wtlx.domain.entity.memory.enums.MemoryType;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索路 A · 语义路（画像兜底 + 向量召回）。
 * <p>
 * 主路，负责"用户换个说法也能命中同一偏好"——"住得舒服点"与"偏好品质住宿"
 * 字面无关，只有向量空间能把它们拉近。
 * <p>
 * <b>为什么这一条路要发两次查询</b>：它要同时回答两个问题——
 * "这个人长期是什么样"（与本轮问句无关）和"这轮他在问什么"（与本轮问句相关）。
 * 后者靠向量相似度，前者<b>只能</b>靠"高置信 × 高重要度"排序：没有任何词面或语义手段
 * 能把「帮我规划一下行程」和「用户不吃辣」联系起来，这正是向量检索的盲区。
 * 少了这一半，新会话的第一句往往问不出画像，跨会话复用就退化成碰运气。
 * <p>
 * <b>画像排在前、语义排在后</b>：融合阶段按排名加权，画像先占住靠前名次，
 * 才不会被"这轮恰好命中很多条行程类记忆"挤掉。最终注入上下文时还会再按冷热分层重排
 * （见 {@code MemoryContextBuilder}），所以这里的顺序只决定"谁能在 topN 里活下来"。
 * <p>
 * 两支都可能失败（PG 抖动、embedding 服务不可用）：此时本路返回空列表或仅画像部分，
 * 另一条路照常工作——记忆是增强，不是依赖。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class VectorMemoryPath implements MemoryPath {

    private static final String PATH_NAME = "vector";

    @Resource
    private MemoryProperties memoryProperties;

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Override
    public String name() {
        return PATH_NAME;
    }

    @Override
    public double weight() {
        return memoryProperties.getVectorWeight();
    }

    @Override
    public List<ScoredMemory> retrieve(String subjectKey, String query, String queryVectorLiteral, int topK) {
        List<ScoredMemory> hits = new ArrayList<>(profileFloor(subjectKey));

        if (StringUtils.hasText(queryVectorLiteral)) {
            hits.addAll(userMemoryMapper.searchByVector(subjectKey, queryVectorLiteral, topK,
                    memoryProperties.getVectorThreshold()));
        } else {
            // embedding 不可用：本路退化为"只有画像"，关键词路继续工作
            log.debug("查询向量缺失，语义路仅返回画像兜底部分");
        }

        // 两支可能命中同一条（高置信偏好往往也与本轮问句相关）：按 memoryId 去重，保留先出现的那次
        Map<String, ScoredMemory> unique = new LinkedHashMap<>(hits.size());
        for (ScoredMemory hit : hits) {
            UserMemory memory = hit.memory();
            if (memory != null && memory.getMemoryId() != null) {
                unique.putIfAbsent(memory.getMemoryId(), hit);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 画像兜底：取长期有效的高价值偏好，与本轮问句无关。
     * <p>
     * 多取一倍再按稳定层过滤：{@code searchStable} 的排序键是 {@code 置信度 × 重要度}，
     * 不区分类型，混进来的行程类记忆会在过滤后让画像条数不足。
     */
    private List<ScoredMemory> profileFloor(String subjectKey) {
        int floorSize = memoryProperties.getStableFloorSize();
        return userMemoryMapper.searchStable(subjectKey, floorSize * 2).stream()
                .filter(hit -> isProfile(hit.memory()))
                .limit(floorSize)
                .toList();
    }

    /**
     * 是否属于稳定层画像（长期有效）。
     * <p>
     * 口径与 {@code MemoryType.stable} 标记同源——不在 SQL 里硬编码类型清单，
     * 否则"新增一个稳定类型"要改两处，而漏改的那处会静默丢画像。
     */
    private static boolean isProfile(UserMemory memory) {
        return memory != null
                && memory.getValidTo() == null
                && MemoryType.fromCode(memory.getMemoryType()).isStable();
    }
}