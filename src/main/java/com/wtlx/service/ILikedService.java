package com.wtlx.service;

/**
 * 点赞服务接口
 * 提供博客、评论、游记等内容的点赞功能
 * 点赞数据存储在Redis中，通过定时任务持久化到数据库
 *
 * @author 金小梁
 */
public interface ILikedService {

    /**
     * 点赞类型常量：博客
     */
    String TYPE_BLOG = "blog";

    /**
     * 点赞类型常量：评论
     */
    String TYPE_COMMENT = "comment";

    /**
     * 点赞类型常量：游记
     */
    String TYPE_TRAVEL = "travel";

    /**
     * 点赞内容
     *
     * @param type   内容类型：blog-博客, comment-评论, travel-游记
     * @param id     内容ID
     * @param userId 用户ID
     * @return true-点赞成功, false-取消点赞
     */
    boolean like(String type, Long id, Long userId);

    /**
     * 判断用户是否已点赞
     *
     * @param type   内容类型
     * @param id     内容ID
     * @param userId 用户ID
     * @return true-已点赞, false-未点赞
     */
    boolean isLiked(String type, Long id, Long userId);

    /**
     * 获取内容的点赞数
     *
     * @param type 内容类型
     * @param id   内容ID
     * @return 点赞数
     */
    Long getLikedCount(String type, Long id);

    /**
     * 同步点赞数到数据库（定时任务调用）
     *
     * @return 同步的记录数
     */
    int syncLikedCountToDb();
}
