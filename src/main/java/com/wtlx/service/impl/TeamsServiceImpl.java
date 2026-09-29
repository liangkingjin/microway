package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.dto.Result;
import com.wtlx.entity.TeamMembers;
import com.wtlx.entity.Teams;
import com.wtlx.entity.User;
import com.wtlx.mapper.TeamMembersMapper;
import com.wtlx.mapper.TeamsMapper;
import com.wtlx.service.ITeamsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.service.IUserService;
import com.wtlx.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 队伍信息表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@Service
public class TeamsServiceImpl extends ServiceImpl<TeamsMapper, Teams> implements ITeamsService {
    @Resource
    private TeamsMapper teamsMapper;
    @Resource
    private TeamMembersMapper teamMemberMapper;

    @Resource
    private IUserService userService;

    /**
     * 获取队伍列表
     * @return
     */
    @Override
    public Result getTeamsList() {
        List<Teams> list = query().eq("status", 1).list();
        return Result.ok(list);
    }

    /**
     * 获取用户加入或者创建的队伍
     * @return
     */
    @Override
    public Result getTeamsOfUser() {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 查询用户创建的队伍
        List<Teams> createdTeams = teamsMapper.selectList(
                new LambdaQueryWrapper<Teams>()
                        .eq(Teams::getCreatorId, userId)
                        .eq(Teams::getStatus, 1)
        );

        // 查询用户加入的队伍（作为成员但不是创建者）
        List<TeamMembers> joinedMembers = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMembers>()
                        .eq(TeamMembers::getUserId, userId)
                        .eq(TeamMembers::getStatus, 1)
                        .eq(TeamMembers::getIsLeader, 0)
        );

        // 获取用户加入的队伍ID列表
        List<Integer> joinedTeamIds = joinedMembers.stream()
                .map(TeamMembers::getTeamId)
                .collect(Collectors.toList());

        // 查询用户加入的队伍详情
        List<Teams> joinedTeams = new ArrayList<>();
        if (!joinedTeamIds.isEmpty()) {
            joinedTeams = teamsMapper.selectList(
                    new LambdaQueryWrapper<Teams>()
                            .in(Teams::getId, joinedTeamIds)
                            .eq(Teams::getStatus, 1)
            );
        }

        // 合并两个列表并转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();

        // 处理创建的队伍
        for (Teams team : createdTeams) {
            Map<String, Object> teamMap = convertTeamToMap(team);
            teamMap.put("isCreator", true); // 标记为创建者
            result.add(teamMap);
        }

        // 处理加入的队伍
        for (Teams team : joinedTeams) {
            Map<String, Object> teamMap = convertTeamToMap(team);
            teamMap.put("isCreator", false); // 标记为非创建者
            result.add(teamMap);
        }

        return Result.ok(result);
    }

    /**
     * 创建队伍
     * @param teams
     * @return
     */
    @Override
    public Result creatTeam(Teams teams) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 设置队伍创建者ID
        teams.setCreatorId(Math.toIntExact(userId));

        // 设置初始状态
        teams.setStatus(1);
        teams.setCurrentMembers(1); // 初始成员数为1（创建者自己）

        // 保存队伍信息
        boolean saved = save(teams);
        if (!saved) {
            return Result.fail("创建队伍失败");
        }

        // 从用户表中获取用户头像
        User user = userService.getById(userId);
        String userAvatar = user != null ? user.getIcon() : null;

        // 创建队伍成员记录（创建者作为队长）
        TeamMembers teamMember = new TeamMembers();
        teamMember.setTeamId(teams.getId());
        teamMember.setUserId(Math.toIntExact(userId));
        teamMember.setName(teams.getCreatorName());
        teamMember.setPhone(teams.getCreatorPhone());
        teamMember.setQq(teams.getCreatorQq());
        // 优先使用用户表中的头像
        teamMember.setAvatar(userAvatar != null ? userAvatar : teams.getCreatorAvatar());
        teamMember.setIsLeader(1); // 设置为队长
        teamMember.setStatus(1);
        teamMember.setJoinTime(LocalDateTime.now()); // 设置加入时间

        // 保存队伍成员信息
        boolean memberSaved = teamMemberMapper.insert(teamMember) > 0;
        if (!memberSaved) {
            // 如果成员保存失败，回滚队伍信息
            removeById(teams.getId());
            return Result.fail("创建队伍成员失败");
        }

        // 返回创建成功的队伍信息
        Map<String, Object> teamMap = convertTeamToMap(teams);
        teamMap.put("isCreator", true);

        return Result.ok(teamMap);
    }

    /**
     * 解散队伍
     * @param teamId 队伍ID
     * @param userId 用户ID（队长ID）
     * @return 是否成功
     */
    @Override
    @Transactional
    public boolean disbandTeam(Long teamId, Long userId) {
        // 1. 查询队伍是否存在
        Teams team = teamsMapper.selectById(teamId);
        if (team == null) {
            return false;
        }

        // 2. 验证当前用户是否为队长
//        if (!team.getCreatorId().equals(userId)) {
//            return false;
//        }

        // 3. 删除所有队伍成员
        LambdaQueryWrapper<TeamMembers> memberQueryWrapper = new LambdaQueryWrapper<>();
        memberQueryWrapper.eq(TeamMembers::getTeamId, teamId);
        teamMemberMapper.delete(memberQueryWrapper);

        // 4. 删除队伍
        int result = teamsMapper.deleteById(teamId);

        return result > 0;
    }

    /**
     * 将Team对象转换为Map，方便添加额外字段
     * @param team 队伍对象
     * @return 转换后的Map
     */
    private Map<String, Object> convertTeamToMap(Teams team) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", team.getId());
        map.put("name", team.getName());
        map.put("creatorName", team.getCreatorName());
        map.put("creatorPhone", team.getCreatorPhone());
        map.put("creatorQQ", team.getCreatorQq());
        map.put("creatorAvatar", team.getCreatorAvatar());
        map.put("destination", team.getDestination());
        map.put("description", team.getDescription());
        map.put("maxMembers", team.getMaxMembers());
        map.put("currentMembers", team.getCurrentMembers());
        map.put("departureDate", team.getDepartureDate());
        map.put("avatar", team.getAvatar());
        map.put("createTime", team.getCreateTime());
        return map;
    }


}
