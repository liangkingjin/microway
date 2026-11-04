package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IBlogService extends IService<Blog> {

    /**
     * 查询个人博客
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 点赞
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询所有博客
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 分页查询全部游记（首页双列流）：批内粗排 + 批次缓存，游标翻页
     * @param cursor 翻页游标，首页传空
     * @param pageSize 每页条数
     * @return {list: 游记列表, hasMore: 是否还有更多, cursor: 下一页游标}
     */
    Result queryHotBlogByCursor(String cursor, Integer pageSize);

    /**
     * 点赞列表查询
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 发表博客
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 分页查询收件箱
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogFollow(Long max, Integer offset);

    Result deleteBlog(Long id);
}
