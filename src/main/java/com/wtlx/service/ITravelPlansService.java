package com.wtlx.service;

import com.wtlx.dto.Result;
import com.wtlx.entity.TravelPlans;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-07
 */
public interface ITravelPlansService extends IService<TravelPlans> {

    /**
     * 查询所有的旅行计划
     * @return
     */
    Result listAllPlans();

    /**
     * 根据id查询旅行计划
     * @param id
     * @return
     */
    Result queryTravelPlansById(Integer id);

    /**
     * 添加旅行计划
     * @param travelPlans
     * @return
     */
    Result addTravelPlans(TravelPlans travelPlans);

    /**
     * 删除旅行计划
     * @param id
     * @return
     */
    Result deleteTravelPlans(Integer id);
}
