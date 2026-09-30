package com.wtlx.rag;

import java.util.List;

/**
 * RAG 相似度检索服务
 *
 * @author 金小梁
 */
public interface RagSearchService {

    /**
     * 相似度检索
     *
     * @param query      查询文本
     * @param topK       返回条数
     * @param threshold  相似度阈值（0~1，越大越严）
     * @param docTypes   来源类型过滤；为空表示不过滤
     * @return 命中列表，按相似度降序；未命中返回空列表
     */
    List<RagHit> search(String query, int topK, double threshold, List<String> docTypes);

    /**
     * 使用默认参数（配置中的 searchTopK / searchThreshold）检索全部来源
     *
     * @param query 查询文本
     * @return 命中列表
     */
    List<RagHit> search(String query);
}
