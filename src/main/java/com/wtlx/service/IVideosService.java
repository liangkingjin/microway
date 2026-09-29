package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.Videos;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 视频表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-05
 */
public interface IVideosService extends IService<Videos> {

    /**
     * 获取所有视频
     * @return
     */
    Result getVideos();

    Result publishVideo(Videos videos);
}
