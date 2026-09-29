package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.dto.ScenicSpotQuery;
import com.wtlx.entity.ScenicSpots;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 景点信息表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-01
 */
public interface IScenicSpotsService extends IService<ScenicSpots> {

    /**
     * 分页查询景点信息
     * @param scenicSpotQuery
     * @return
     */
    Result listAll(ScenicSpotQuery scenicSpotQuery);
}
