package com.wtlx.controller;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.TbBlogComments;
import com.wtlx.service.ILikedService;
import com.wtlx.service.ITbBlogCommentsService;
import com.wtlx.utils.UserHolder;
import com.wtlx.domain.entity.vo.CommentVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tb-blog-comments")
@Api(tags = "博客评论模块接口")
public class TbBlogCommentsController {

    @Autowired
    private ITbBlogCommentsService commentsService;

    @Autowired
    private ILikedService likedService;

    // 获取评论列表（包含主评论和回复，前端自行组装树结构）
    @GetMapping("/list")
    @ApiOperation("获取评论列表")
    public Result list(@RequestParam Long blogId) {
        List<CommentVO> list = commentsService.listByBlogIdWithUser(blogId);
        return Result.ok(list);
    }

    // 新增评论或回复
    @PostMapping
    @ApiOperation("新增评论或回复")
    public Result add(@RequestBody TbBlogComments comment) {
        boolean success = commentsService.saveComment(comment);
        return success ? Result.ok() : Result.fail("评论失败");
    }

    // 点赞评论
    @PostMapping("/like/{id}")
    @ApiOperation("点赞评论")
    public Result likeComment(@PathVariable Long id) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 执行点赞/取消点赞操作
        boolean isLike = likedService.like(ILikedService.TYPE_COMMENT, id, userId);
        return Result.ok(isLike);
    }

    // 获取评论点赞数
    @GetMapping("/like/count/{id}")
    @ApiOperation("获取评论点赞数")
    public Result getCommentLikeCount(@PathVariable Long id) {
        Long count = likedService.getLikedCount(ILikedService.TYPE_COMMENT, id);
        return Result.ok(count);
    }
}