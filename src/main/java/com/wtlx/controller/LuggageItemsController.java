package com.wtlx.controller;


import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.LuggageItems;
import com.wtlx.service.ILuggageItemsService;
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
@Api(tags = "行李模块接口")
@RestController
@RequestMapping("/luggage-items")
public class LuggageItemsController {
    @Resource
    private ILuggageItemsService luggageItemsService;

    /**
     * 展示所有的行李
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("展示所有的行李")
    public Result listAllLuggageItems(@PathVariable Integer id) {
        return luggageItemsService.listAllLuggageItems(id);
    }


    @PostMapping
    @ApiOperation("添加行李")
    public Result addLuggageItems(@RequestBody LuggageItems luggageItems) {
        return luggageItemsService.addLuggageItems(luggageItems);
    }

    /**
     * 修改行李
     * @param id
     * @param luggageItems
     * @return
     */
    @PutMapping("/{id}")
    @ApiOperation("修改行李")
    public Result updateLuggageItems(@PathVariable Integer id, @RequestBody LuggageItems luggageItems) {
        return luggageItemsService.updateLuggageItems(id, luggageItems);
    }

    /**
     * 删除行李
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除行李")
    public Result deleteLuggageItems(@PathVariable Integer id) {
        return luggageItemsService.deleteLuggageItems(id);
    }
}
