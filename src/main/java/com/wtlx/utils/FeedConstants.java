package com.wtlx.utils;

/**
 * 游记双列流的召回与粗排参数。
 */
public class FeedConstants {

    /**
     * 每批召回条数，也是单批缓存的容量
     */
    public static final int BLOG_BATCH_SIZE = 100;

    /**
     * 单页最大条数
     */
    public static final int BLOG_MAX_PAGE_SIZE = 50;

    /**
     * 粗排权重：互动
     */
    public static final double BLOG_WEIGHT_INTERACTION = 0.55D;

    /**
     * 粗排权重：新鲜度
     */
    public static final double BLOG_WEIGHT_FRESHNESS = 0.35D;

    /**
     * 粗排权重：首图质量
     */
    public static final double BLOG_WEIGHT_IMAGE = 0.10D;

    /**
     * 新鲜度半衰期（小时），10 天
     */
    public static final double BLOG_FRESHNESS_HALF_LIFE_HOURS = 240D;
}
