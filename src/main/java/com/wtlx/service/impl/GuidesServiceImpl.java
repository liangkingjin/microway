package com.wtlx.service.impl;

import com.wtlx.entity.Guides;
import com.wtlx.mapper.GuidesMapper;
import com.wtlx.service.IGuidesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-03-13
 */
@Service
public class GuidesServiceImpl extends ServiceImpl<GuidesMapper, Guides> implements IGuidesService {


    @Override
    public List<Guides> queryProvincesByIds(Long id) {
        return query().eq("province_id", id).list();
    }
}
