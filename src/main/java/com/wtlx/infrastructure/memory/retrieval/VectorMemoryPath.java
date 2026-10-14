package com.wtlx.infrastructure.memory.retrieval;

import com.wtlx.domain.entity.memory.ScoredMemory;
import com.wtlx.infrastructure.memory.config.MemoryProperties;
import com.wtlx.repository.mapper.UserMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 检索路 A · 向量语义路。
 * <p>
 * 主路。负责"用户换个说法也能命中同一偏好"——"住得舒服点"与"偏好品质住宿"
 * 字面无关、槽位也可能不同，只有向量空间能把它们拉近。
 * <p>
 * 权重最高但不设唯一：向量检索在中文短句上其实相当脆弱（
 * "我不吃辣" 与 "我喜欢辣" 的余弦相似度往往高于 0.7，仅凭向量无法区分否定），
 * 因此必须与槽位路、关键词路交叉验证，冲突时由 RRF 的排名投票收敛。
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
        if (!StringUtils.hasText(queryVectorLiteral)) {
            // embedding 不可用：本路空转，其余三路继续工作，检索整体降级但不失效
            log.debug("查询向量缺失，向量路跳过");
            return List.of();
        }
        return userMemoryMapper.searchByVector(subjectKey, queryVectorLiteral, topK,
                memoryProperties.getVectorThreshold());
    }
}
