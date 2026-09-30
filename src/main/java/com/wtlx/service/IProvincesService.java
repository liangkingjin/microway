package com.wtlx.service;

import com.wtlx.domain.entity.Provinces;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IProvincesService extends IService<Provinces> {

    /**
     * 查询所有省份
     * @return
     */
    List<Provinces> queryProvinces();
}
