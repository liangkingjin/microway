package com.wtlx.infrastructure.task;

import com.wtlx.service.impl.UserCheckInsServiceImpl;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Slf4j
public class CheckInsTask {

    @Autowired
    private UserCheckInsServiceImpl userCheckInsService;

    @XxlJob("updateCheckInsJob")
    public void updateCheckIns() {
        log.info("更新打卡状态：{}", new Date());
        try {
            userCheckInsService.update().set("has_checked_in", 0).update();
            XxlJobHelper.handleSuccess("打卡状态更新成功");
        } catch (Exception e) {
            log.error("更新打卡状态失败", e);
            XxlJobHelper.handleFail("更新打卡状态失败: " + e.getMessage());
        }
    }
}
