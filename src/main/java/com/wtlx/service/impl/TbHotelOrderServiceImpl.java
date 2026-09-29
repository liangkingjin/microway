package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wtlx.dto.Result;
import com.wtlx.entity.Shop;
import com.wtlx.entity.TbHotelOrder;
import com.wtlx.entity.TbHotelRoom;
import com.wtlx.mapper.ShopMapper;
import com.wtlx.mapper.TbHotelOrderMapper;
import com.wtlx.mapper.TbHotelRoomMapper;
import com.wtlx.service.ITbHotelOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.UserHolder;
import com.wtlx.vo.HotelOrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 酒店订单表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-15
 */
@Service
public class TbHotelOrderServiceImpl extends ServiceImpl<TbHotelOrderMapper, TbHotelOrder> implements ITbHotelOrderService {


    @Autowired
    private ShopMapper hotelMapper;

    @Autowired
    private TbHotelRoomMapper roomMapper;
    /**
     * 预定酒店
     * @param tbHotelOrder
     * @return
     */
    @Override
    public Result OrderHotelRoom(TbHotelOrder tbHotelOrder) {
        //判断当前用户是否已经订购了该房间
        if (query().eq("user_id", UserHolder.getUser().getId()).eq("shop_id", tbHotelOrder.getShopId()).eq("room_id", tbHotelOrder.getRoomId()).count()>0){
            return Result.fail("您已经预定了该房间");
        }
        tbHotelOrder.setUserId(UserHolder.getUser().getId());
        tbHotelOrder.setStatus(2);
        save(tbHotelOrder);
        return Result.ok();
    }

    @Override
    public Result queryUserHotelOrders(Integer page, Integer size) {
        Page<TbHotelOrder> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<TbHotelOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TbHotelOrder::getUserId, UserHolder.getUser().getId())
                .orderByDesc(TbHotelOrder::getCreateTime);

        // 执行分页查询
        this.page(pageInfo, wrapper);

        // 转换为VO列表
        List<HotelOrderVO> voList = pageInfo.getRecords().stream().map(order -> {
            HotelOrderVO vo = new HotelOrderVO();
            // 基础字段拷贝
            BeanUtils.copyProperties(order, vo);

            // 查询关联数据
            Shop hotel = hotelMapper.selectById(order.getShopId());
            TbHotelRoom room = roomMapper.selectById(order.getRoomId());

            // 设置关联字段
            vo.setHotelName(hotel != null ? hotel.getName() : "未知酒店");
            vo.setRoomType(room != null ? room.getRoomType() : "未知房型");
            vo.setStatusText(getStatusText(order.getStatus()));

            return vo;
        }).collect(Collectors.toList());

        return Result.ok(voList);
    }

    private String getStatusText(Integer status) {
        switch (status) {
            case 0: return "待支付";
            case 1: return "已支付待确认";
            case 2: return "已确认";
            case 3: return "已取消";
            case 4: return "已完成";
            default: return "未知状态";
        }
    }


}
