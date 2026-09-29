package com.wtlx.utils;

import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于Guava RateLimiter的令牌桶限流器
 * 包装Google Guava库提供的限流实现，提供更友好的API
 *
 * 令牌桶算法原理：
 * - 桶中存放令牌，每秒产生固定数量的令牌
 * - 请求到来时尝试从桶中获取令牌
 * - 获取成功则允许通过，获取失败则拒绝
 *
 * 优化点：
 * 1. 使用ConcurrentHashMap缓存限流器实例，避免重复创建
 * 2. 提供静态工厂方法，自动管理实例生命周期
 * 3. 线程安全，支持高并发场景
 *
 * @author 金小梁
 */
public class RateLimiter {

    /**
     * 限流器缓存，使用Key-Value形式存储不同业务场景的限流器
     * Key: 业务标识（如景点ID）
     * Value: 对应的限流器实例
     */
    private static final ConcurrentMap<String, RateLimiter> limiterCache = new ConcurrentHashMap<>();

    /**
     * 真正的Guava限流器实例
     */
    private final com.google.common.util.concurrent.RateLimiter rateLimiter;

    /**
     * 每秒生成的令牌数量（速率）
     * -- GETTER --
     *  获取当前配置的令牌产生速率
     *
     * @return 每秒产生的令牌数量

     */
    @Getter
    private final double permitsPerSecond;

    /**
     * 构造方法
     *
     * @param permitsPerSecond 每秒产生的令牌数量
     */
    public RateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        // 创建Guava限流器，permitsPerSecond为每秒令牌数
        this.rateLimiter = com.google.common.util.concurrent.RateLimiter.create(permitsPerSecond);
    }

    /**
     * 获取限流器实例（推荐使用此方法）
     * 使用缓存机制，避免重复创建相同的限流器
     *
     * @param key               业务唯一标识（如景点ID、接口路径等）
     * @param permitsPerSecond 每秒产生的令牌数量
     * @return 对应业务key的限流器实例
     */
    public static RateLimiter getInstance(String key, double permitsPerSecond) {
        // computeIfAbsent原子性操作，保证线程安全
        return limiterCache.computeIfAbsent(key, k -> new RateLimiter(permitsPerSecond));
    }

    /**
     * 尝试获取一个令牌（非阻塞）
     * 如果桶中有令牌则立即返回true，否则返回false
     *
     * @return true获取成功，false获取失败
     */
    public boolean tryAcquire() {
        return rateLimiter.tryAcquire();
    }

    /**
     * 尝试获取指定数量的令牌（非阻塞）
     *
     * @param permits 要获取的令牌数量
     * @return true获取成功，false获取失败
     */
    public boolean tryAcquire(int permits) {
        return rateLimiter.tryAcquire(permits);
    }

    /**
     * 尝试获取令牌（带超时）
     * 在指定时间内等待令牌可用
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true获取成功，false超时失败
     */
    public boolean tryAcquire(long timeout, java.util.concurrent.TimeUnit unit) {
        return rateLimiter.tryAcquire(timeout, unit);
    }

    /**
     * 获取一个令牌（阻塞）
     * 如果桶中有令牌则立即返回，否则等待令牌产生
     */
    public void acquire() {
        rateLimiter.acquire();
    }

    /**
     * 获取指定数量的令牌（阻塞）
     *
     * @param permits 要获取的令牌数量
     */
    public void acquire(int permits) {
        rateLimiter.acquire(permits);
    }

}
