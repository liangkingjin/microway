package com.wtlx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wtlx.entity.dto.Result;
import com.wtlx.entity.PhotoSpots;
import com.wtlx.entity.PlaceRecommendations;
import com.wtlx.mapper.PhotoSpotsMapper;
import com.wtlx.mapper.PlaceRecommendationsMapper;
import com.wtlx.service.IPlaceRecommendationsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.entity.vo.PlaceRecommendationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-14
 */
@Service
public class PlaceRecommendationsServiceImpl extends ServiceImpl<PlaceRecommendationsMapper, PlaceRecommendations> implements IPlaceRecommendationsService {
    @Resource
    private PhotoSpotsMapper photoSpotsMapper;

    /**
     * 根据地点id查询推荐榜单
     * @param placeId
     * @return
     */
    @Transactional
    @Override
    public Result queryRecommendationsById(Integer placeId) {
        //1.查询点位信息
        List<PhotoSpots> photoSpots = photoSpotsMapper.selectList(new QueryWrapper<PhotoSpots>()
                .eq("place_id", placeId));
        //2.根据id查询推荐榜单信息
        PlaceRecommendations placeRecommendations = getOne(new QueryWrapper<PlaceRecommendations>()
                .eq("id", placeId));
        //3.构建返回VO
        PlaceRecommendationVO placeRecommendationVO = new PlaceRecommendationVO();
        BeanUtil.copyProperties(placeRecommendations, placeRecommendationVO);
        placeRecommendationVO.setPhotoSpots(photoSpots);
        return Result.ok(placeRecommendationVO);
    }
}
