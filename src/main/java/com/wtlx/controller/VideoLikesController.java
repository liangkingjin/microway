package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.service.IVideoLikesService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 * 视频点赞记录表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
@Api(tags = "视频点赞模块接口")
@RestController
@RequestMapping("/video-likes")
public class VideoLikesController {
    @Resource
    private IVideoLikesService videoLikesService;

    @GetMapping("{id}")
    @ApiOperation(value = "点赞视频")
    public Result addVideoLikes(@PathVariable Long id) {
        return videoLikesService.addVideoLikes(id);
    }
}
