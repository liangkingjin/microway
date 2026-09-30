package com.wtlx.service;

import com.wtlx.domain.entity.ThumbsUpRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 通用点赞记录表 服务类
 * </p>
 *
 * @author 金小梁
 */
public interface IThumbsUpRecordService extends IService<ThumbsUpRecord> {

    /**
     * 异步更新业务表点赞数
     *
     * @param type   点赞类型
     * @param bizId  业务ID
     * @param isLike true-点赞，false-取消点赞
     */
    void asyncUpdateLikeCount(String type, Long bizId, boolean isLike);

    /**
     * 保存或更新点赞记录
     *
     * @param type   点赞类型
     * @param bizId  业务ID
     * @param userId 用户ID
     * @param status 状态：1-点赞，0-取消点赞
     */
    void saveOrUpdateLikeRecord(String type, Long bizId, Long userId, Integer status);
}
