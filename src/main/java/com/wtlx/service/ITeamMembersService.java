package com.wtlx.service;

import com.wtlx.entity.dto.Result;
import com.wtlx.entity.dto.TeamJoinRequest;
import com.wtlx.entity.TeamMembers;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 队伍成员表 服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
public interface ITeamMembersService extends IService<TeamMembers> {

    /**
     * 获取队伍成员列表
     * @return
     */
    Result getTeamMembersList(Integer id);

    Result joinTeam(TeamJoinRequest joinRequest);

    boolean quitTeam(Long teamId, Long userId);
}
