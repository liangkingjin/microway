package com.wtlx.controller;


import com.wtlx.entity.dto.Result;
import com.wtlx.entity.ItineraryItems;
import com.wtlx.service.IItineraryItemsService;
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
@Api(tags = "行程安排模块接口")
@RestController
@RequestMapping("/itinerary-items")
public class ItineraryItemsController {
    @Resource
    private IItineraryItemsService itineraryItemsService;


    /**
     * 查询所有行程安排
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("查询所有行程安排")
    public Result listAllItineraryItems(@PathVariable Integer id) {
        return itineraryItemsService.listAllItineraryItems(id);
    }

    /**
     * 删除行程安排
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除行程安排")
    public Result deleteItineraryItems(@PathVariable Integer id) {
        return itineraryItemsService.deleteItineraryItems(id);
    }

    /**
     * 修改行程安排
     * @param id
     * @return
     */
    @PutMapping("/{id}")
    @ApiOperation("修改行程安排")
    public Result updateItineraryItems(@PathVariable Integer id, @RequestBody ItineraryItems itineraryItems) {
       return itineraryItemsService.updateItineraryItems(id, itineraryItems);
    }


    /**
     *  添加行程安排
     * @param itineraryItems
     * @return
     */
    @PostMapping
    @ApiOperation("添加行程安排")
    public Result addItineraryItems(@RequestBody ItineraryItems itineraryItems) {
        return itineraryItemsService.addItineraryItems(itineraryItems);
    }

}
