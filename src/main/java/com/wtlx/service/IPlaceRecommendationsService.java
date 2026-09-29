package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.PlaceRecommendations;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IPlaceRecommendationsService extends IService<PlaceRecommendations> {

    /**
     * 根据placeId查询推荐榜单
     * @param placeId
     * @return
     */
    Result queryRecommendationsById(Integer placeId);
}
