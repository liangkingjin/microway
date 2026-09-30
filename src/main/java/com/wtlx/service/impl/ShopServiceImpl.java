package com.wtlx.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.dto.SearchParams;
import com.wtlx.domain.entity.Shop;
import com.wtlx.repository.mapper.ShopMapper;
import com.wtlx.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.CacheClient;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.RedisData;
import com.wtlx.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**
     * 根据id查询店铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop=queryWithLogicExpire(id);

        //缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //逻辑过期解决缓存击穿
        //Shop shop=cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //互斥锁解决缓存击穿
        Shop shop = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 更新店铺信息
     * @param shop
     */
    @Override
    @Transactional
    public Result updateByShopId(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 店铺类型id
     * @param current 当前页码
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //检查经纬度是否为空
        if(x==null||y==null){
            //如果经纬度为空的话，则根据店铺类型进行分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回查询结果，Result.ok()是自定义的返回结果封装类，用于返回成功的响应
            return Result.ok(page.getRecords());
        }

        // 构建 Redis 中存储店铺地理位置信息的 key
        String key=RedisConstants.SHOP_GEO_KEY+typeId;

        // 计算当前页在 Redis 查询结果中的起始和结束位置
        int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;

        // 使用 Redis 的 Geo 功能进行地理位置查询
        // radius 方法用于查询指定 key 中距离指定点（x, y）在 5000 米范围内的店铺
        // includeDistance() 表示查询结果中包含距离信息
        // limit(end) 表示最多返回 end 条记录
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo()
                .radius(key,
                        new Circle(new Point(x, y),1000000000),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance().limit(end));
        //检查查询结果是否为空
        if(search==null){
            //如果为空的话，返回一个空列表
            return Result.ok(Collections.emptyList());
        }

        // 存储查询到的店铺 ID
        List<Long> ids = new ArrayList<>();
        // 存储店铺 ID 与对应的距离信息
        Map<String, Distance> distanceMap = new HashMap<>();
        //获取查询结果中的内容
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        if(content.size()<=from){
            //没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        //跳过前面 from 条记录，只处理当前页的记录
        content.stream().skip(from).forEach(item->{
            //获取店铺的id
            String shopId = item.getContent().getName();
            //将店铺id转换为Long类型并添加到ids列表中
            ids.add(Long.valueOf(shopId));
            //获取店铺与指定点的距离
            Distance distance = item.getDistance();
            //将店铺id与距离信息存储到distanceMap
            distanceMap.put(shopId,distance);
        });

        // 检查 ids 列表是否为空
        if (ids.isEmpty()){
            return Result.ok();
        }

        // 将 ids 列表中的元素用逗号连接成一个字符串
        String idsStr = StrUtil.join(",", ids);

        // 根据店铺 ID 列表查询店铺信息
        // in("id", ids) 表示查询 id 在 ids 列表中的记录
        // last("ORDER BY FIELD(id," + idsStr + ")") 表示按照 idsStr 中店铺 ID 的顺序对查询结果进行排序
        List<Shop> shopList = query().in("id", ids).last("order by field(id," + idsStr + ")").list();

        // 遍历查询到的店铺列表，为每个店铺设置距离信息
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        //返回查到的店铺列表
        return Result.ok(shopList);

    }

    @Override
    public Result searchShop(SearchParams searchParams) {
        List<Shop> shopList = query().like("name", searchParams.getName()).eq("type_id", searchParams.getTypeId()).list();
        return Result.ok(shopList);
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 重建缓存
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id){
        String key=RedisConstants.CACHE_SHOP_KEY + id;
        //1. 从redis中查询是否有商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //缓存命中
        //判断是否为空值
        if (StrUtil.isNotBlank(shopJson)){
            //2. 有，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值，null代表redis中没查到，“”代表redis中有值但是为空值返回错误
        if(shopJson!=null){
            return null;
        }
        //3.没有的话查询数据库
        Shop shop = getById(id);
        if (shop == null){
            //4.没有的话,将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        //5.有，写入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回商铺信息
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id){
        String key=RedisConstants.CACHE_SHOP_KEY + id;
        //1. 从redis中查询是否有商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //缓存命中
        //判断是否为空值
        if (StrUtil.isNotBlank(shopJson)){
            //2. 有，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值，null代表redis中没查到，“”代表redis中有值但是为空值返回错误
        if(shopJson!=null){
            return null;
        }
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //二次查询redis，在获取锁之前就有线程完成了重建
            if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(key))){
                //2. 有，直接返回
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return shop;
            }
            //3.成功，查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            if (shop == null){
                //4.没有的话,将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
                return null;
            }
            //5.有，写入redis
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key,jsonStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //6.返回商铺信息
        return shop;
    }

    /**
     * 逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    private Shop queryWithLogicExpire(Long id) {
        String key=RedisConstants.CACHE_SHOP_KEY + id;
        //1. 从redis中查询是否有商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.缓存命中
        //判断是否为空值
        if (StrUtil.isBlank(shopJson)){
            //未命中直接返回null
            return null;
        }
        //4.命中
        //4.1 先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.2判断是否到期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.未过期，直接返回店铺信息
            return shop;
        }
        //6.过期
        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断是否获取成功
        if(isLock){
            //成功，二次查询redis，放置在获取锁之前就有线程完成了重建
            RedisData redisData2 = JSONUtil.toBean(shopJson, RedisData.class);
            Shop shop2 = JSONUtil.toBean((JSONObject) redisData2.getData(), Shop.class);
            LocalDateTime expireTime2 = redisData2.getExpireTime();
            //判断是否到期
            if(expireTime2.isAfter(LocalDateTime.now())){
                //未过期，直接返回店铺信息
                return shop2;
            }
            //无，需要重建
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });

        }
        //7.返回商铺信息
        return shop;
    }

}
