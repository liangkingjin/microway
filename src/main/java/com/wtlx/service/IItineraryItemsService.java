package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.ItineraryItems;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IItineraryItemsService extends IService<ItineraryItems> {

    /**
     * 查询所有行程安排
     * @param id
     * @return
     */
    Result listAllItineraryItems(Integer id);

    /**
     * 删除行程安排
     * @param id
     * @return
     */
    Result deleteItineraryItems(Integer id);

    /**
     * 修改行程安排
     * @param id
     * @param itineraryItems
     * @return
     */
    Result updateItineraryItems(Integer id, ItineraryItems itineraryItems);

    /**
     * 添加行程安排
     * @param itineraryItems
     * @return
     */
    Result addItineraryItems(ItineraryItems itineraryItems);
}
