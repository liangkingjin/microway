package com.wtlx.service;

import com.wtlx.domain.entity.Guides;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IGuidesService extends IService<Guides> {

    /**
     * 根据省份id查询攻略
     * @param id
     * @return
     */
    List<Guides> queryProvincesByIds(Long id);
}
