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
 * 抽取池（{@code memoryExtractionExecutor}）：任务耗时以秒计且会调 LLM，
 * 必须低并发 + 大队列，且绝不能反向阻塞对话线程。
 * 检索不再另起线程池——它已在对话收尾 / 拦截器链路里同步执行，且单路向量召回足够快，
 * 再包一层异步只会增加复杂度与排障成本。
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
}
