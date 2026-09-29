package com.wtlx.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wtlx.entity.dto.Result;
import com.wtlx.entity.dto.UserDTO;
import com.wtlx.entity.Blog;
import com.wtlx.service.IBlogService;
import com.wtlx.utils.SystemConstants;
import com.wtlx.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-3-26
 */
@Api(tags = "博客模块接口")
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发表博客
     * @param blog
     * @return
     */
    @ApiOperation("发表文章")
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    @ApiOperation("点赞")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询所有博客
     * @param current
     * @return
     */
    @GetMapping("/hot")
    @ApiOperation("查询所有博客")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询个人攻略及博客
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("查询个人博客")
    public Result queryBlogById(@PathVariable Long id){
        return blogService.queryBlogById(id);
    }

    /**
     * 点赞列表查询
     * @param id
     * @return
     */
    @ApiOperation("点赞列表查询")
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 根据id查询博主的探店笔记
     * @param current
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 分页查询收件箱
     * @param max
     * @param offset
     * @return
     */
    @ApiOperation("分页查询收件箱")
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,@RequestParam(value = "offset",defaultValue = "0") Integer offset
    ){
        return blogService.queryBlogFollow(max,offset);
    }

    /**
     * 删除博客
     * @param id 博客ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除博客")
    public Result deleteBlog(@PathVariable("id") Long id) {
        return blogService.deleteBlog(id);
    }


}
