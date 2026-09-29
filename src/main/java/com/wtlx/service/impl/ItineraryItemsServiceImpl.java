package com.wtlx.service.impl;

import com.wtlx.dto.Result;
import com.wtlx.entity.ItineraryItems;
import com.wtlx.mapper.ItineraryItemsMapper;
import com.wtlx.service.IItineraryItemsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
@Service
public class ItineraryItemsServiceImpl extends ServiceImpl<ItineraryItemsMapper, ItineraryItems> implements IItineraryItemsService {


    /**
     * 查询所有行程安排
     * @param id
     * @return
     */
    @Override
    public Result listAllItineraryItems(Integer id) {
        List<ItineraryItems> itineraryItemsList = query().eq("plan_id", id).list();
        return Result.ok(itineraryItemsList);
    }

    /**
     * 删除行程安排
     * @param id
     * @return
     */
    @Override
    public Result deleteItineraryItems(Integer id) {
        if (id == null) {
            return Result.fail("参数错误");
        }
        return Result.ok(removeById(id));
    }

    /**
     * 修改行程安排
     * @param id
     * @param itineraryItems
     * @return
     */
    @Override
    public Result updateItineraryItems(Integer id, ItineraryItems itineraryItems) {
        if (id == null || itineraryItems == null) {
            return Result.fail("参数错误");
        }
        itineraryItems.setId(id);
        return Result.ok(updateById(itineraryItems));
    }

    /**
     * 添加行程安排
     * @param itineraryItems
     * @return
     */
    @Override
    public Result addItineraryItems(ItineraryItems itineraryItems) {
        if (itineraryItems == null) {
            return Result.fail("参数错误");
        }
        return Result.ok(save(itineraryItems));
    }


}
