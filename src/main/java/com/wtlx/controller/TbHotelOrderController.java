package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.entity.TbHotelOrder;
import com.wtlx.entity.TbHotelRoom;
import com.wtlx.service.ITbHotelOrderService;
import com.wtlx.service.ITbHotelRoomService;
import com.wtlx.vo.HotelOrderDetailVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 酒店订单表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
@RestController
@RequestMapping("/tb-hotel-order")
@Api(tags = "酒店订单表")
public class TbHotelOrderController {
    @Resource
    private ITbHotelOrderService tbHotelOrderService;
    @Resource
    private ITbHotelRoomService hotelRoomService;

    @PostMapping
    @ApiOperation("下单")
    private Result OrderHotelRoom(@RequestBody TbHotelOrder tbHotelOrder){
        return tbHotelOrderService.OrderHotelRoom(tbHotelOrder);
    }

    @GetMapping("/of/user")
    @ApiOperation("分页查询用户订单")
    public Result queryUserHotelOrders(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return tbHotelOrderService.queryUserHotelOrders(page, size);
    }


    @GetMapping("/detail/{id}")
    @ApiOperation("获取酒店订单详情")
    public Result getOrderDetail(@PathVariable Integer id) {
        TbHotelOrder order = tbHotelOrderService.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        HotelOrderDetailVO vo = new HotelOrderDetailVO();
        BeanUtils.copyProperties(order, vo);

        // 查询关联的房间信息
        TbHotelRoom room = hotelRoomService.getById(order.getRoomId());
        if (room != null) {
            vo.setRoomType(room.getRoomType());
            vo.setTitle(room.getTitle());
            vo.setSize(room.getSize());
            vo.setBedInfo(room.getBedInfo());
            vo.setCapacity(room.getCapacity());
            vo.setMealInfo(room.getMealInfo());
            vo.setCancelInfo(room.getCancelInfo());
            vo.setFloor(room.getFloor());
            vo.setFacilities(room.getFacilities());
        }

        return Result.ok(vo);
    }

}
