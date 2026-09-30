package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询所有店铺类型
     * @return
     */
    Result queryTypeList();

}
