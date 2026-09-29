package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.entity.Videos;
import com.wtlx.service.IVideosService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 视频表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
@Api(tags = "视频模块接口")
@RestController
@RequestMapping("/videos")
public class VideosController {
    @Resource
    private IVideosService videosService;

    /**
     * 获取所有视频
     * @return
     */
    @GetMapping
    @ApiOperation(value = "获取所有视频")
    public Result getAllVideos(){
        return videosService.getVideos();
    }


    /**
     * 发布视频
     * @param videos 视频对象
     * @return 结果
     */
    @PostMapping
    @ApiOperation(value = "发布视频")
    public Result publishVideo(@RequestBody Videos videos){
        return videosService.publishVideo(videos);
    }
}
