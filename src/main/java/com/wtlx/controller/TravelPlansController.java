package com.wtlx.controller;


import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.TravelPlans;
import com.wtlx.service.ITravelPlansService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
@Api(tags = "旅行计划模块接口")
@RestController
@RequestMapping("/travel-plans")
public class TravelPlansController {
    @Resource
    private ITravelPlansService travelPlansService;

    /**
     * 查询所有旅行计划
     * @return
     */
    @GetMapping
    @ApiOperation("查询所有旅行计划")
    public Result queryTravelPlans() {
        return travelPlansService.listAllPlans();
    }


    @GetMapping("/{id}")
    @ApiOperation("根据id查询旅行计划")
    public Result queryTravelPlansById(@PathVariable Integer id) {
        return travelPlansService.queryTravelPlansById(id);
    }

    @PostMapping
    @ApiOperation("添加旅行计划")
    public Result addTravelPlans(@RequestBody TravelPlans travelPlans) {
        return travelPlansService.addTravelPlans(travelPlans);
    }
    @ApiOperation("删除旅行计划")
    @DeleteMapping("/{id}")
    public Result deleteTravelPlans(@PathVariable Integer id) {
        return travelPlansService.deleteTravelPlans(id);
    }

}
