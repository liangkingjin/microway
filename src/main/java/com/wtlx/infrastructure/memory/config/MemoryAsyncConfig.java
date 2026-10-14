package com.wtlx.infrastructure.memory.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 长期记忆异步线程池配置。
 * <p>
 * 拆成两个池而不是共用一个，原因是两类任务的资源诉求完全相反：
 * <ul>
 *     <li><b>抽取池</b>（{@code memoryExtractionExecutor}）：任务耗时以秒计且会调 LLM，
 *         必须低并发 + 大队列，且绝不能反向阻塞对话线程；</li>
 *     <li><b>检索池</b>（{@code memoryRetrievalExecutor}）：任务是短查询、在对话链路的关键路径上，
 *         需要高并发 + 小队列 + 快速失败，超时即降级为无长期记忆。</li>
 * </ul>
 * 若共用一个池，抽取任务把线程占满会让检索在关键时刻排队，直接拖慢对话首字延迟。
 *
 * @author 金小梁
 */
@Slf4j
@Configuration
public class MemoryAsyncConfig {

    /**
     * 记忆抽取线程池：LLM 调用为主，低并发防打爆配额
     */
    @Bean(name = "memoryExtractionExecutor")
    public Executor memoryExtractionExecutor(MemoryProperties memoryProperties) {
        int poolSize = Math.max(1, memoryProperties.getExtractionPoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("memory-extract-");
        // 队列满时由调用方（对话收尾线程）执行：宁可让收尾慢一点，也不能静默丢记忆
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 记忆检索线程池：多路并行检索复用，短任务快速失败
     */
    @Bean(name = "memoryRetrievalExecutor")
    public Executor memoryRetrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("memory-retrieval-");
        // 队列满时由调用线程执行：检索是对话主路径的一部分，不能因为池满而丢掉长期记忆
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
