package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.dto.Result;
import com.wtlx.entity.Expenses;
import com.wtlx.entity.ItineraryItems;
import com.wtlx.entity.LuggageItems;
import com.wtlx.entity.TravelPlans;
import com.wtlx.mapper.ExpensesMapper;
import com.wtlx.mapper.ItineraryItemsMapper;
import com.wtlx.mapper.LuggageItemsMapper;
import com.wtlx.mapper.TravelPlansMapper;
import com.wtlx.service.ITravelPlansService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
public class TravelPlansServiceImpl extends ServiceImpl<TravelPlansMapper, TravelPlans> implements ITravelPlansService {
    @Resource
    private ExpensesMapper expensesMapper;
    @Resource
    private ItineraryItemsMapper itineraryItemsMapper;
    @Resource
    private LuggageItemsMapper luggageItemsMapper;

    /**
     * 查询所有旅行计划
     * @return
     */
    @Override
    public Result listAllPlans() {
        Long userId = UserHolder.getUser().getId();
        List<TravelPlans> travelPlansList = query().eq("user_id", userId).list();
        return Result.ok(travelPlansList);
    }

    /**
     * 根据id查询旅行计划
     * @param id
     * @return
     */
    @Override
    public Result queryTravelPlansById(Integer id) {
        TravelPlans travelPlans = query().eq("id", id).one();
        return Result.ok(travelPlans);
    }

    /**
     * 添加旅行计划
     * @param travelPlans
     * @return
     */
    @Override
    public Result addTravelPlans(TravelPlans travelPlans) {
        travelPlans.setUserId(Math.toIntExact(UserHolder.getUser().getId()));
        save(travelPlans);
        return Result.ok();
    }

    @Transactional
    @Override
    public Result deleteTravelPlans(Integer id) {
        // 删除主表记录
        removeById(id);

        // 删除关联的Expenses记录
        expensesMapper.delete(new LambdaQueryWrapper<Expenses>().eq(Expenses::getPlanId, id));

        // 删除关联的ItineraryItems记录
        itineraryItemsMapper.delete(new LambdaQueryWrapper<ItineraryItems>().eq(ItineraryItems::getPlanId, id));

        // 删除关联的LuggageItems记录
        luggageItemsMapper.delete(new LambdaQueryWrapper<LuggageItems>().eq(LuggageItems::getPlanId, id));

        return Result.ok();
    }

}
