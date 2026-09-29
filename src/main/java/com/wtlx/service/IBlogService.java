package com.wtlx.service;

import com.wtlx.dto.Result;
import com.wtlx.entity.Blog;
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
