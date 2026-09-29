package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.entity.Teams;
import com.wtlx.service.ITeamsService;
import com.wtlx.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 队伍信息表 前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@RestController
@RequestMapping("/teams")
@Api(tags = "队伍模块接口")
public class TeamsController {
    @Resource
    private ITeamsService teamsService;

    @GetMapping("/list")
    @ApiOperation("获取队伍列表")
    public Result getTeamsList(){
        return teamsService.getTeamsList();
    }

    @GetMapping("/of/user")
    @ApiOperation("获取用户加入或者创建的队伍")
    public Result getTeamsOfUser(){
        return teamsService.getTeamsOfUser();
    }

    @PostMapping("/create")
    @ApiOperation("创建队伍")
    public Result creatTeam(@RequestBody Teams teams){
        return teamsService.creatTeam(teams);
    }

    /**
     * 解散队伍
     * @param teamId 队伍ID
     * @return 操作结果
     */
    @PostMapping("/disband/{teamId}")
    public Result disbandTeam(@PathVariable Long teamId) {
        // 从Session获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        // 调用服务层方法解散队伍
        boolean success = teamsService.disbandTeam(teamId, userId);

        if (success) {
            return Result.ok();
        } else {
            return Result.fail("解散队伍失败，您可能不是队长或队伍不存在");
        }
    }

}
