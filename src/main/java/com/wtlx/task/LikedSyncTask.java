package com.wtlx.task;

import com.wtlx.service.ILikedService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class LikedSyncTask {

    @Resource
    private ILikedService likedService;

    @XxlJob("syncLikedCountJob")
    public void syncLikedCount() {
        log.info("开始执行点赞数据同步任务...");
        long startTime = System.currentTimeMillis();

        try {
            int count = likedService.syncLikedCountToDb();
            log.info("点赞数据同步任务执行完成，共同步{}条记录，耗时{}ms", count, System.currentTimeMillis() - startTime);
            XxlJobHelper.handleSuccess("同步成功，共同步" + count + "条记录");
        } catch (Exception e) {
            log.error("点赞数据同步任务执行失败", e);
            XxlJobHelper.handleFail("同步失败: " + e.getMessage());
        }
    }
}
