package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.TbHotelRoom;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 酒店房间表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
public interface ITbHotelRoomService extends IService<TbHotelRoom> {

    /**
     * 根据店铺id和房间id查询房间信息
     * @param shopId
     * @param roomId
     * @return
     */
    Result queryHotelRoomById(Long shopId, Long roomId);

    /**
     * 根据店铺id查询房间信息
     * @param shopId
     * @return
     */
    Result queryAllHotelRoom(Long shopId);

}
