package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.service.IPlaceRecommendationsService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-14
 */
@Api(tags = "推荐榜单")
@RestController
@RequestMapping("/place-recommendations")
public class PlaceRecommendationsController {
    @Resource
    private IPlaceRecommendationsService placeRecommendationsService;

    /**
     * 查询所有的推荐榜单内容
     * @return
     */
    @GetMapping
    public Result queryRecommendations(){
        return Result.ok(placeRecommendationsService.list());
    }

    /**
     * 根据placeId查询推荐榜单
     * @param placeId
     * @return
     */
    @GetMapping("/place/{placeId}")
    public Result queryRecommendationsById(@PathVariable Integer placeId){
        return placeRecommendationsService.queryRecommendationsById(placeId);
    }
}
