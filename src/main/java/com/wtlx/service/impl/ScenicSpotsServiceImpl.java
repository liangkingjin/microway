package com.wtlx.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.wtlx.dto.Result;
import com.wtlx.dto.ScenicSpotQuery;
import com.wtlx.entity.ScenicSpots;
import com.wtlx.mapper.ScenicSpotsMapper;
import com.wtlx.service.IScenicSpotsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 景点信息表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-01
 */
@Service
public class ScenicSpotsServiceImpl extends ServiceImpl<ScenicSpotsMapper, ScenicSpots> implements IScenicSpotsService {

    /**
     * 查询所有景点信息
     * @param scenicSpotQuery
     * @return
     */
    @Override
    public Result listAll(ScenicSpotQuery scenicSpotQuery) {
        //分页查询景点信息
        List<ScenicSpots> list = query().like("name", scenicSpotQuery.getKeyword()).list();
        if(scenicSpotQuery.getKeyword()==null){
            list = query().list();
        }
        if (list.isEmpty()) {
            return Result.fail("没有查询到相关数据");
        }
        return Result.ok(list);

    }
}
