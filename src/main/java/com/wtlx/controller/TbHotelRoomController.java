package com.wtlx.controller;


import com.wtlx.entity.dto.Result;
import com.wtlx.service.ITbHotelRoomService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 酒店房间表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
@RestController
@RequestMapping("/tb-hotel-room")
@Api(tags = "酒店房间表")
public class TbHotelRoomController {
    @Resource
    private ITbHotelRoomService tbHotelRoomService;

    /**
     * 根据酒店id和房间id查询房间信息
     * @param shopId
     * @param roomId
     * @return
     */
    @GetMapping("/{shopId}/{roomId}")
    public Result queryHotelRoomById(@PathVariable Long shopId,@PathVariable Long roomId){
        return tbHotelRoomService.queryHotelRoomById(shopId,roomId);
    }

    /**
     * 查询所有酒店房间
     * @param shopId
     * @return
     */
    @GetMapping("/{shopId}")
    public Result queryAllHotelRoom(@PathVariable Long shopId){
        return tbHotelRoomService.queryAllHotelRoom(shopId);
    }


}
