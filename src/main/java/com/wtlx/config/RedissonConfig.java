package com.wtlx.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        log.info("正在初始化Redisson");
        //配置
        Config config = new Config();
        config.useSingleServer()
                //.setAddress("redis://127.0.0.1:6379")
                .setAddress("redis://192.168.150.101:6379")
                .setPassword("123321")
                .setDatabase(0);
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
