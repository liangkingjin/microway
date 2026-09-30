package com.wtlx.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 向量检索命中结果
 *
 * @author 金小梁
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 命中切片文本
     */
    private String text;

    /**
     * 相似度（0~1，越大越相关）
     */
    private double score;

    /**
     * 切片元数据：docType / sourceId / title / cover / chunkIndex 等
     */
    private Map<String, Object> metadata;
}
