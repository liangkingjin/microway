package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.LuggageItems;
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

    /**
     * 修改行李
     * @param id 行李id
     * @param luggageItems 待修改内容
     * @return
     */
    Result updateLuggageItems(Integer id, LuggageItems luggageItems);

    /**
     * 删除行李
     * @param id 行李id
     * @return
     */
    Result deleteLuggageItems(Integer id);
}
