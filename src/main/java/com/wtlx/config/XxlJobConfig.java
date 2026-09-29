package com.wtlx.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${tj.xxl-job.admin.address:}")
    private String adminAddresses;

    @Value("${tj.xxl-job.access-token:tianji}")
    private String accessToken;

    @Value("${tj.xxl-job.executor.appname:}")
    private String appname;

    @Value("${tj.xxl-job.executor.logPath:}")
    private String logPath;

    @Value("${tj.xxl-job.executor.log-retention-days:10}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info("XXL-Job配置初始化...");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        return xxlJobSpringExecutor;
    }
}
