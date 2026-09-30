package com.wtlx.constant;

/**
 * RAG 检索来源类型
 *
 * @author 金小梁
 */
public final class RagDocType {

    /**
     * 站内已发布的旅游游记（tb_blog）
     */
    public static final String BLOG = "BLOG";

    /**
     * 官方攻略（guides）
     */
    public static final String GUIDE = "GUIDE";

    /**
     * 索引状态：待处理
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 索引状态：已入库
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 索引状态：入库失败，等待补偿
     */
    public static final String STATUS_FAILED = "FAILED";

    private RagDocType() {
    }
}
