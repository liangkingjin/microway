package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.dto.ScenicSpotQuery;
import com.wtlx.entity.ScenicSpots;
import com.wtlx.service.IScenicSpotsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 * 景点信息表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-01
 */
@RestController
@RequestMapping("/scenic-spots")
@Api(tags = "景点模块接口")
public class ScenicSpotsController {

    @Resource
    private IScenicSpotsService scenicSpotsService;


    /**
     * 查询景点列表
     */
    @ApiOperation("查询景点列表")
    @GetMapping
    public Result list(ScenicSpotQuery scenicSpotQuery){
        return scenicSpotsService.listAll(scenicSpotQuery);
    }

    /**
     * 查询景点详情
     * @param id
     * @return
     */
    @ApiOperation("查询景点详情")
    @GetMapping("/{id}")
    public Result getById(@PathVariable Integer id){
        ScenicSpots scenicSpots = scenicSpotsService.getById(id);
        return Result.ok(scenicSpots);
    }



}
