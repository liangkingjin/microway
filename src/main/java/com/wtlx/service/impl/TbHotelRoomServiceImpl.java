package com.wtlx.service.impl;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.TbHotelRoom;
import com.wtlx.repository.mapper.TbHotelRoomMapper;
import com.wtlx.service.ITbHotelRoomService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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
