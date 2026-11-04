package com.wtlx.utils;

import cn.hutool.core.util.RandomUtil;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = RandomUtil.randomLong(200, 600);

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * 门票秒杀库存key前缀
     * 格式：stock:scenic:{景点ID}:{游玩日期}
     */
    public static final String STOCK_SCENIC_KEY = "stock:scenic:";

    /**
     * 门票秒杀订单key前缀
     * 格式：order:scenic:{景点ID}:{游玩日期}
     * 使用Set集合存储用户ID，实现一人一单
     */
    public static final String ORDER_SCENIC_KEY = "order:scenic:";

    /**
     * 博客点赞用户集合（Set）- 用于快速判断用户是否点赞
     * key格式：liked:blog:user:{blogId}
     * value：用户ID集合
     */
    public static final String BLOG_LIKED_USER_KEY = "liked:blog:user:";

    /**
     * 博客点赞数量聚合ZSet（按业务类型聚合）
     * key格式：likes:blog
     * member：实体ID（如博客ID）
     * score：点赞数量
     */
    public static final String BLOG_LIKED_COUNT_KEY = "likes:blog";

    /**
     * 评论点赞用户集合（Set）
     */
    public static final String COMMENT_LIKED_USER_KEY = "liked:comment:user:";

    /**
     * 评论点赞数量聚合ZSet
     */
    public static final String COMMENT_LIKED_COUNT_KEY = "likes:comment";

    /**
     * 游记点赞用户集合（Set）
     */
    public static final String TRAVEL_LIKED_USER_KEY = "liked:travel:user:";

    /**
     * 游记点赞数量聚合ZSet
     */
    public static final String TRAVEL_LIKED_COUNT_KEY = "likes:travel";

    /**
     * 游记双列流批次缓存（ZSet）
     * key格式：feed:blog:{用户ID}:{批次起点时间戳}_{批次起点ID}
     * member：游记ID:创建时间戳，score：批内粗排序位
     */
    public static final String FEED_BLOG_BATCH_KEY = "feed:blog:";

    /**
     * 游记批次缓存存活时间（秒），过期只影响内存回收，不影响翻页连续性
     */
    public static final Long FEED_BLOG_BATCH_TTL = 600L;

    /**
     * 游记批次 ZSet 成员格式：游记ID + 分隔符 + 创建时间戳
     */
    public static final String FEED_BLOG_MEMBER_SEPARATOR = ":";

    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    public static final String LOCK_KEY_PREFIX = "lock:";

    public static final String SECKILL_ORDER_KEY = "seckill:order:";

}
