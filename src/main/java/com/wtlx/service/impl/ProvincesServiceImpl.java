package com.wtlx.service.impl;

import com.wtlx.entity.Provinces;
import com.wtlx.mapper.ProvincesMapper;
import com.wtlx.service.IProvincesService;
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
public class ProvincesServiceImpl extends ServiceImpl<ProvincesMapper, Provinces> implements IProvincesService {

    /**
     * 查询所有省份
     * @return
     */
    @Override
    public List<Provinces> queryProvinces() {
        List<Provinces> list = query().list();
        return list;
    }
}
