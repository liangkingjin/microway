package com.wtlx.rag;

import com.wtlx.entity.User;
import com.wtlx.entity.dto.ai.TripCardVO;
import com.wtlx.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索命中 → 前端旅行卡片。
 * <p>
 * 同一来源的多段切片只出一张卡片（保留最高分），卡片顺序即相似度顺序。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class RagCardAssembler {

    /**
     * 卡片摘要最大长度
     */
    private static final int SUMMARY_LIMIT = 80;

    @Resource
    private IUserService userService;

    /**
     * 组装卡片
     *
     * @param hits 检索命中；为空时返回空列表
     * @return 卡片列表，按相似度降序
     */
    public List<TripCardVO> assemble(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        // LinkedHashMap 保持相似度顺序，同时按 来源类型:来源id 去重
        Map<String, TripCardVO> cards = new LinkedHashMap<>();
        for (RagHit hit : hits) {
            Map<String, Object> metadata = hit.getMetadata();
            String docType = str(metadata.get("docType"));
            Long sourceId = longVal(metadata.get("sourceId"));
            if (sourceId == null) {
                continue;
            }
            String key = docType + ":" + sourceId;
            TripCardVO existed = cards.get(key);
            if (existed != null) {
                if (hit.getScore() > existed.getScore()) {
                    existed.setScore(hit.getScore());
                }
                continue;
            }
            cards.put(key, TripCardVO.builder()
                    .sourceType(docType)
                    .sourceId(sourceId)
                    .title(str(metadata.get("title")))
                    .cover(str(metadata.get("cover")))
                    .authorName(resolveAuthor(docType, metadata))
                    .liked(intVal(metadata.get("liked")))
                    .summary(abbreviate(hit.getText()))
                    .score(hit.getScore())
                    .build());
        }
        return new ArrayList<>(cards.values());
    }

    private String resolveAuthor(String docType, Map<String, Object> metadata) {
        Long authorId = longVal(metadata.get("authorId"));
        if (authorId == null) {
            return "";
        }
        try {
            User user = userService.getById(authorId);
            return user == null ? "" : (user.getNickName() == null ? "" : user.getNickName());
        } catch (Exception e) {
            log.debug("查询卡片作者失败, authorId: {}", authorId);
            return "";
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_LIMIT ? flat : flat.substring(0, SUMMARY_LIMIT) + "…";
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intVal(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
