package com.wtlx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 * 用于配置秒杀订单的异步处理线程池
 *
 * @author 金小梁
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 订单异步处理线程池
     * 用于执行秒杀成功后的订单创建操作，将数据库操作异步化
     *
     * 线程池参数说明：
     * - 核心线程数：10（常驻线程数量）
     * - 最大线程数：20（高峰期最大线程数）
     * - 队列容量：200（等待执行的队列大小）
     * - 线程存活时间：60秒
     * - 拒绝策略：CallerRunsPolicy（由调用线程执行）
     *
     * @return Executor 线程池实例
     */
    @Bean(name = "orderAsyncExecutor")
    public Executor orderAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：一直存在的线程数量
        executor.setCorePoolSize(10);
        // 最大线程数：高峰期可以创建的最多线程数
        executor.setMaxPoolSize(20);
        // 队列容量：等待执行的任务队列大小
        executor.setQueueCapacity(200);
        // 线程存活时间：超过核心线程数的空闲线程存活时间
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀：便于排查问题
        executor.setThreadNamePrefix("order-async-");
        // 拒绝策略：当线程池满时，由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }
}
