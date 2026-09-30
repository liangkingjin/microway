package com.wtlx.controller;


import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.Orders;
import com.wtlx.service.IOrdersService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-03
 */
@Api(tags = "门票订单模块接口")
@RestController
@RequestMapping("/orders")
public class OrdersController {
    @Resource
    private IOrdersService ordersService;


    /**
     * 秒杀门票
     * @param orders 订单信息（包含scenicId、visitDate、totalQuantity）
     * @return
     */
    @PostMapping
    @ApiOperation(value = "秒杀门票")
    public Result seckill(@RequestBody Orders orders){
        return ordersService.seckill(orders.getScenicId(), orders.getVisitDate(), orders.getTotalQuantity());
    }



    // 分页查询用户门票订单
    @GetMapping("/of/user")
    @ApiOperation("查询用户门票订单")
    public Result queryTicketOrders(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return ordersService.queryUserTicketOrders(page, size);
    }

    // 取消门票订单
    @PostMapping("/ticket/cancel/{id}")
    @ApiOperation("取消门票订单")
    public Result cancelTicketOrder(@PathVariable Long id) {
        return ordersService.cancelTicketOrder(id);
    }

    // 新增订单详情接口
    @GetMapping("/{id}")
    @ApiOperation("获取订单详情")
    public Result getOrderDetail(@PathVariable Long id) {
        return ordersService.getOrderDetail(id);
    }
}
