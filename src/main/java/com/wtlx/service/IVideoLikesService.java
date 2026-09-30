package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.VideoLikes;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 视频点赞记录表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
public interface IVideoLikesService extends IService<VideoLikes> {

    /**
     * 点赞视频
     * @param id
     * @return
     */
    public Result addVideoLikes(Long id);

}
