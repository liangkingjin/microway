package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wtlx.dto.Result;
import com.wtlx.entity.TbHotelOrder;
import com.wtlx.entity.TbHotelRoom;
import com.wtlx.mapper.TbHotelRoomMapper;
import com.wtlx.service.ITbHotelRoomService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 酒店房间表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
@Service
public class TbHotelRoomServiceImpl extends ServiceImpl<TbHotelRoomMapper, TbHotelRoom> implements ITbHotelRoomService {

    /**
     * 根据酒店id查询酒店房间
     * @param shopId
     * @param roomId
     * @return
     */
    @Override
    public Result queryHotelRoomById(Long shopId, Long roomId) {
        List<TbHotelRoom> tbHotelRooms = query().eq("shop_id", shopId).eq("id", roomId).list();
        return Result.ok(tbHotelRooms);
    }

    /**
     * 根据酒店id查询酒店房间
     * @param shopId
     * @return
     */
    @Override
    public Result queryAllHotelRoom(Long shopId) {
        List<TbHotelRoom> tbHotelRooms = query().eq("shop_id", shopId).list();
        return Result.ok(tbHotelRooms);
    }



}
