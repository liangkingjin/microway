package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.infrastructure.memory.support.TextKeywords;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索路 C · 关键词兜底路。
 * <p>
 * 用中文 n-gram 直接匹配记忆正文，按命中词数排序。
 * <p>
 * 存在的意义是"向量路失效时记忆不至于整体消失"：embedding 服务抖动、配额耗尽、
 * 或中文专有名词（"稻城亚丁""赛里木湖"）被向量稀释时，这条路仍能把含该词面的记忆捞回来。
 * <p>
 * 权重最低是有意的：它只有词面匹配能力，无法理解同义与否定，
 * 权重过高会让"提到成都"的闲聊把"不喜欢成都"的避讳记忆错误地排到最前。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class KeywordMemoryPath implements MemoryPath {

    private static final String PATH_NAME = "keyword";

    /**
     * 单次检索使用的最多关键词数：过多会让 OR 语义过宽，几乎命中全库
     */
    private static final int MAX_KEYWORDS = 8;

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
        return memoryProperties.getKeywordWeight();
    }

    @Override
    public List<ScoredMemory> retrieve(String subjectKey, String query, String queryVectorLiteral, int topK) {
        List<String> keywords = TextKeywords.extract(query, MAX_KEYWORDS);
        if (keywords.isEmpty()) {
            log.debug("查询未抽取到有效关键词，关键词路跳过");
            return List.of();
        }
        List<ScoredMemory> hits = userMemoryMapper.searchByKeywords(subjectKey, TextKeywords.join(keywords), topK);
        log.debug("关键词路命中关键词 {}，召回 {} 条", keywords, hits.size());
        return hits;
    }
}
