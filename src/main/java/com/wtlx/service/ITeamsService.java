package com.wtlx.service;

import com.wtlx.dto.Result;
import com.wtlx.entity.Teams;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 队伍信息表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
public interface ITeamsService extends IService<Teams> {

    /**
     * 获取队伍列表
     * @return
     */
    Result getTeamsList();

    /**
     * 获取用户加入或者创建的队伍
     * @return
     */
    Result getTeamsOfUser();

    Result creatTeam(Teams teams);

    boolean disbandTeam(Long teamId, Long userId);
}
