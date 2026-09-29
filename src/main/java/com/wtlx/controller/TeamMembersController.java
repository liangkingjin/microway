package com.wtlx.controller;


import com.wtlx.entity.dto.Result;
import com.wtlx.entity.dto.TeamJoinRequest;
import com.wtlx.service.ITeamMembersService;
import com.wtlx.utils.UserHolder;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 队伍成员表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@Api(tags = "队伍成员模块接口")
@RestController
@RequestMapping("/team-members")
public class TeamMembersController {
    @Resource
    private ITeamMembersService teamMembersService;

    @GetMapping("/{id}")
    public Result getTeamMembersList(@PathVariable Integer id) {
        return teamMembersService.getTeamMembersList(id);
    }

    /**
     * 加入队伍
     */
    @PostMapping("/join")
    public Result joinTeam(@RequestBody TeamJoinRequest joinRequest) {
        return teamMembersService.joinTeam(joinRequest);
    }

    /**
     * 退出队伍
     * @param teamId 队伍ID
     * @return 操作结果
     */
    @PostMapping("/quit/{teamId}")
    public Result quitTeam(@PathVariable Long teamId) {
        // 从Session获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        // 调用服务层方法退出队伍
        boolean success = teamMembersService.quitTeam(teamId, userId);

        if (success) {
            return Result.ok("退出队伍成功");
        } else {
            return Result.fail("退出队伍失败");
        }
    }

}
