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
     * 游标分页查询所有博客（按 id 倒序，lastId 为上一页最后一条的 id，为空则查第一页）
     * @param lastId 游标（上一页最后一条博客 id），可为空
     * @param pageSize 每页条数
     * @return {list: 博客列表, hasMore: 是否还有更多}
     */
    Result queryHotBlogByCursor(Long lastId, Integer pageSize);

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
