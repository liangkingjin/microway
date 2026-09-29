package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.TbHotelOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 酒店订单表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
public interface ITbHotelOrderService extends IService<TbHotelOrder> {

    /**
     * 预定酒店
     * @param tbHotelOrder
     * @return
     */
    Result OrderHotelRoom(TbHotelOrder tbHotelOrder);


    Result queryUserHotelOrders(Integer page, Integer size);
}
