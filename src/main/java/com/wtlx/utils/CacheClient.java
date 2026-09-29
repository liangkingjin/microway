package com.wtlx.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

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
     * java对象序列化成json字符串并存入redis,普通存入
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * java对象序列化成json字符串并存入redis,特殊存入，有逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 利用缓存空值的方式来解决缓存穿透
     * @param keyPrefix 缓存前缀
     * @param id   缓存店铺id
     * @param type 缓存类型
     * @param dbFallback 查询数据库这个操作
     * @param time 过期时间
     * @param unit 时间单位
     * @return
     * @param <R>
     * @param <ID>
     */
    public  <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key=keyPrefix+ id;
        //1. 从redis中查询是否有商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //缓存命中
        //判断是否为空值
        if (StrUtil.isNotBlank(Json)){
            //2. 有，直接返回
            return JSONUtil.toBean(Json, type);
        }
        //判断是否是空值，null代表redis中没查到，“”代表redis中有值但是为空值返回错误
        if(Json!=null){
            return null;
        }
        //3.没有的话查询数据库
        R r= dbFallback.apply(id);
        if (r == null){
            //4.没有的话,将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        //5.有，写入redis
        this.set(key,r,time,unit);
        //6.返回商铺信息
        return r;
    }

    /**
     * 利用逻辑过期解决缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public  <R,ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key=keyPrefix+id;
        //1. 从redis中查询是否有商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.缓存命中
        //判断是否为空值
        if (StrUtil.isBlank(Json)){
            //3.未命中，直接返回null
            return null;
        }
        //4.命中
        //4.1 先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.2判断是否到期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.未过期，直接返回店铺信息
            return r;
        }
        //6.过期
        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断是否获取成功
        if(isLock){
            //成功，二次查询redis，放置在获取锁之前就有线程完成了重建
            Json = stringRedisTemplate.opsForValue().get(key);
            RedisData redisData2 = JSONUtil.toBean(Json, RedisData.class);
            R r2 = JSONUtil.toBean((JSONObject) redisData2.getData(), type);
            LocalDateTime expireTime2 = redisData2.getExpireTime();
            //判断是否到期
            if(expireTime2.isAfter(LocalDateTime.now())){
                //未过期，直接返回店铺信息
                return r2;
            }

            //无，需要重建
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R newR= dbFallback.apply(id);
                    //重建缓存
                    this.setWithLogicExpire(key,newR,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });

        }
        //7.返回商铺信息
        return r;
    }

    public <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix + id;
        //1. 从redis中查询是否有商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //缓存命中
        //判断是否为空值
        if (StrUtil.isNotBlank(Json)){
            //2. 有，直接返回
            return JSONUtil.toBean(Json, type);
        }
        //判断是否是空值，null代表redis中没查到，“”代表redis中有值但是为空值返回错误
        if(Json!=null){
            return null;
        }
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if(!isLock){
                //2.1.获取锁失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            //2.2.获取锁成功，根据id查询数据库
            //二次查询redis，在获取锁之前就有线程完成了重建
            String Json2 = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(Json2)){
                //2. 有，直接返回
                return JSONUtil.toBean(Json2, type);
            }
            //3.还是不行，那就查询数据库
            r = dbFallback.apply(id);
            //模拟重建延时
            Thread.sleep(200);
            if (r == null){
                //4.没有的话,将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
                return null;
            }
            //5.有，写入redis
            this.set(key,r,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //6.返回商铺信息
        return r;
    }
}
