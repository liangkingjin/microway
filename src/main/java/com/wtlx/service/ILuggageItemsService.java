package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.LuggageItems;
import com.baomidou.mybatisplus.extension.service.IService;


public interface ILuggageItemsService extends IService<LuggageItems> {

    /**
     * 查询所有行李
     * @param id
     * @return
     */
    Result listAllLuggageItems(Integer id);

    /**
     * 添加行李
     * @param luggageItems
     * @return
     */
    Result addLuggageItems(LuggageItems luggageItems);
}
