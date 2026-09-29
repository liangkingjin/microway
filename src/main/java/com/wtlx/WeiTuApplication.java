package com.wtlx;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.wtlx.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@Slf4j
@EnableScheduling   //开启任务调度
public class WeiTuApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeiTuApplication.class, args);
    }

}
