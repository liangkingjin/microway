package com.wtlx.service;

import com.wtlx.dto.Result;
import com.wtlx.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

    /**
     * 关注或取关
     * @param followId
     * @param isFollow
     * @return
     */
    Result follow(Long followId, Boolean isFollow);

    /**
     * 查看是否关注
     * @param followId
     * @return
     */
    Result isFollow(Long followId);

    /**
     * 展现出当前用户与博主的共同好友
     * @param id
     * @return
     */
    Result followCommons(Long id);
}
