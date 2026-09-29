package com.wtlx.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wtlx.entity.dto.Result;
import com.wtlx.entity.ShopType;
import com.wtlx.mapper.ShopTypeMapper;
import com.wtlx.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 查询所有店铺类型
     * @return
     */
    @Override
    public Result queryTypeList() {
        String key= RedisConstants.CACHE_SHOP_TYPE_KEY;
        //1. 从redis中查询是否有商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key, 0, -1);
        if (StrUtil.isNotBlank(shopJson)){
            //2. 有，直接返回
            List<ShopType> list = JSONUtil.toList(shopJson, ShopType.class);
            return Result.ok(list);
        }
        //3.没有的话查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        if (list == null){
            //4.没有的话返回404
            return Result.fail("分类类型不存在");
        }
        //5.有，写入redis
        String jsonStr = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
        //6.返回商铺信息
        return Result.ok(list);
    }
}
