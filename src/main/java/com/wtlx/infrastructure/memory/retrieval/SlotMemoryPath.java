package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索路 B · 槽位精确路。
 * <p>
 * 预置"查询词 → 槽位"的映射表，命中即按槽位直取。
 * 与向量路的互补性最强：向量路管"意思相近"，槽位路管"话题明确"。
 * 当用户直接问到"预算多少合适""有什么忌口"时，这条路能稳定命中
 * 对应的 {@code budget.level} / {@code dietary.restriction} 记忆，
 * 且完全不受 embedding 抖动与相似度阈值标定的影响。
 * <p>
 * 槽位表刻意保持小（十余条）且语义单一：映射越宽，越容易把"提到预算这个词"
 * 误判成"询问预算档位"，反倒引入噪声。宁可漏，不可错。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class SlotMemoryPath implements MemoryPath {

    private static final String PATH_NAME = "slot";

    /**
     * 查询词 → 槽位键映射。
     * <p>
     * 键为槽位，值为触发词。触发词必须足够"专指"，例如"住"这类过泛的词刻意不收录。
     * <p>
     * 用静态 LinkedHashMap 而非 {@code Map.of}：条目数超过 10 对，且需要保持声明顺序，
     * 让调试日志里"命中了哪些槽位"的输出稳定可读。
     */
    private static final Map<String, List<String>> SLOT_HINTS;

    static {
        Map<String, List<String>> hints = new LinkedHashMap<>();
        hints.put("budget.level", List.of("预算", "多少钱", "花费", "费用", "花销", "便宜", "性价比", "省钱", "贵不贵"));
        hints.put("dietary.restriction", List.of("吃辣", "饮食", "忌口", "素食", "清真", "海鲜", "不吃", "口味", "过敏"));
        hints.put("accommodation.prefer", List.of("住宿", "酒店", "民宿", "青旅", "客栈", "住哪", "房型"));
        hints.put("transport.prefer", List.of("交通", "高铁", "飞机", "自驾", "火车", "地铁", "包车", "怎么去"));
        hints.put("travel.style", List.of("风格", "穷游", "深度游", "打卡", "旅游方式", "玩法"));
        hints.put("trip.pace", List.of("节奏", "太赶", "悠闲", "紧凑", "休闲", "安排紧", "慢慢玩"));
        hints.put("party.companion", List.of("同行", "一个人", "独自", "和朋友", "女朋友", "男朋友",
                "带孩子", "亲子", "爸妈", "父母", "同事"));
        hints.put("interest.tag", List.of("兴趣", "爱好", "喜欢玩", "喜欢什么", "偏好"));
        hints.put("avoid.prefer", List.of("不喜欢", "避开", "讨厌", "人多", "排队", "受不了", "不想去"));
        hints.put("season.prefer", List.of("季节", "淡季", "旺季", "暑假", "寒假", "五一", "十一", "国庆"));
        hints.put("destination.prefer", List.of("想去哪", "喜欢去哪", "目的地偏好", "最想去"));
        hints.put("trip.plan", List.of("行程计划", "打算去", "准备去", "计划去"));
        SLOT_HINTS = Collections.unmodifiableMap(hints);
    }

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
        return memoryProperties.getSlotWeight();
    }

    @Override
    public List<ScoredMemory> retrieve(String subjectKey, String query, String queryVectorLiteral, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> slots = matchSlots(query);
        if (slots.isEmpty()) {
            log.debug("查询未命中任何预置槽位，槽位路跳过: {}", query);
            return List.of();
        }
        List<ScoredMemory> hits = userMemoryMapper.searchBySlots(subjectKey, String.join(",", slots), topK);
        log.debug("槽位路命中槽位 {}，召回 {} 条", slots, hits.size());
        return hits;
    }

    /**
     * 匹配查询中出现的所有槽位
     */
    private Set<String> matchSlots(String query) {
        Set<String> matched = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : SLOT_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (query.contains(hint)) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }
        return matched;
    }
}
