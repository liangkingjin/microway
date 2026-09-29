package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.entity.LuggageItems;
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
}
