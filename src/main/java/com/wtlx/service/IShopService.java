package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.dto.SearchParams;
import com.wtlx.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询店铺
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新店铺信息
     * @param shop
     */
    Result updateByShopId(Shop shop);


    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    Result searchShop(SearchParams searchParams);
}
