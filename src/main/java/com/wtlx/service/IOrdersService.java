package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.Orders;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IOrdersService extends IService<Orders> {

    /**
     * 购买门票
     * @param orders
     * @return
     */
    Result insert(Orders orders);

    /**
     * 秒杀门票
     * @param scenicId 景点ID
     * @param visitDate 游玩日期
     * @param quantity 购买数量
     * @return
     */
    Result seckill(Long scenicId, String visitDate, Integer quantity);

    Result queryUserTicketOrders(Integer page, Integer size);

    Result cancelTicketOrder(Long id);

    Result getOrderDetail(Long id);
}
