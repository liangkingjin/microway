package com.wtlx.infrastructure.task;

import com.wtlx.infrastructure.memory.cleanup.MemoryCleanupService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户长期记忆清理定时任务。
 * <p>
 * 遵循项目既有约定：任务只做"调用 + 回报"，cron 配在 xxl-job admin（192.168.150.101:8880）上，
 * 不写在代码里（项目里 {@code @EnableScheduling} 开着但没有任何 {@code @Scheduled}）。
 * <p>
 * 建议调度频率：每天凌晨一次。记忆清理是维护性任务，不需要实时性，
 * 但必须稳定执行——漏跑几天不会立刻出问题，连续漏跑一个月记忆质量就会明显退化。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemoryCleanupTask {

    @Resource
    private MemoryCleanupService memoryCleanupService;

    /**
     * 全库记忆清理：过期回收 → 低置信淘汰 → 容量收敛 → 冗余合并 → 物理清除
     */
    @XxlJob("userMemoryCleanupJob")
    public void cleanupUserMemory() {
        log.info("开始执行用户长期记忆清理任务...");
        long start = System.currentTimeMillis();
        try {
            MemoryCleanupService.CleanupStat stat = memoryCleanupService.cleanupAll();
            String message = stat.summary() + "，耗时 " + (System.currentTimeMillis() - start) + "ms";
            log.info(message);
            XxlJobHelper.handleSuccess(message);
        } catch (Exception e) {
            log.error("用户长期记忆清理任务执行失败", e);
            XxlJobHelper.handleFail("清理失败: " + e.getMessage());
        }
    }
}
