package com.wtlx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.dto.Result;
import com.wtlx.dto.TeamJoinRequest;
import com.wtlx.entity.TeamMembers;
import com.wtlx.entity.Teams;
import com.wtlx.entity.User;
import com.wtlx.mapper.TeamMembersMapper;
import com.wtlx.mapper.TeamsMapper;
import com.wtlx.service.ITeamMembersService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.service.ITeamsService;
import com.wtlx.service.IUserService;
import com.wtlx.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 队伍成员表 服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-04-16
 */
@Service
public class TeamMembersServiceImpl extends ServiceImpl<TeamMembersMapper, TeamMembers> implements ITeamMembersService {
    @Resource
    private IUserService userService;
    @Resource
    private ITeamsService teamsService;
    @Resource
    private TeamMembersMapper teamMembersMapper;
    @Resource
    private TeamsMapper teamsMapper;

    /**
     * 获取队伍成员列表
     * @param teamId 队伍ID
     * @return 成员列表结果
     */
    @Override
    public Result getTeamMembersList(Integer teamId) {
        if (teamId == null) {
            return Result.fail("队伍ID不能为空");
        }

        // 查询队伍成员列表
        List<TeamMembers> membersList = query()
                .eq("team_id", teamId)
                .eq("status", 1)
                .orderByDesc("is_leader") // 队长排在前面
                .orderByAsc("join_time") // 其他成员按加入时间排序
                .list();

        // 获取所有成员的用户ID
        List<Integer> userIds = membersList.stream()
                .map(TeamMembers::getUserId)
                .collect(Collectors.toList());

        // 查询用户信息，获取头像
        Map<Long, String> userAvatarMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            // 假设有一个userService或者userMapper可以查询用户信息
            List<User> users = userService.listByIds(userIds);
            // 构建用户ID到头像的映射
            userAvatarMap = users.stream()
                    .collect(Collectors.toMap(
                            User::getId,
                            user -> user.getIcon() != null ? user.getIcon() : "/imgs/default-avatar.jpg"
                    ));
        }

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMembers member : membersList) {
            Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("id", member.getId());
            memberMap.put("name", member.getName());

            // 优先使用成员表中的头像，如果没有则使用用户表中的头像
            String avatar = member.getAvatar();
            if (avatar == null || avatar.isEmpty()) {
                avatar = userAvatarMap.getOrDefault(member.getUserId(), "/imgs/default-avatar.jpg");
            }
            memberMap.put("avatar", avatar);

            memberMap.put("phone", member.getPhone());
            memberMap.put("qq", member.getQq());
            memberMap.put("isLeader", member.getIsLeader() == 1);
            memberMap.put("joinTime", member.getJoinTime());

            result.add(memberMap);
        }

        return Result.ok(result);
    }

    /**
     * 加入队伍
     */
    @Override
    @Transactional
    public Result joinTeam(TeamJoinRequest joinRequest) {
        // 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 验证队伍是否存在
        Teams team = teamsService.getById(joinRequest.getTeamId());
        if (team == null) {
            return Result.fail("队伍不存在");
        }

        // 检查队伍是否已满
        if (team.getCurrentMembers() >= team.getMaxMembers()) {
            return Result.fail("队伍已满，无法加入");
        }

        // 检查用户是否已经在队伍中
        LambdaQueryWrapper<TeamMembers> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamMembers::getTeamId, joinRequest.getTeamId())
                .eq(TeamMembers::getUserId, userId)
                .eq(TeamMembers::getStatus, 1);

        long count = baseMapper.selectCount(queryWrapper);
        boolean isAlreadyMember = count > 0;

        if (isAlreadyMember) {
            return Result.fail("您已经是该队伍成员");
        }


        // 从用户表中获取用户头像
        User user = userService.getById(userId);
        String userAvatar = user != null ? user.getIcon() : null;

        // 创建队伍成员记录
        TeamMembers teamMember = new TeamMembers();
        teamMember.setTeamId(joinRequest.getTeamId());
        teamMember.setUserId(Math.toIntExact(userId));
        teamMember.setName(joinRequest.getName());
        teamMember.setPhone(joinRequest.getPhone());
        teamMember.setQq(joinRequest.getQq());
        teamMember.setRemark(joinRequest.getRemark());
        // 设置用户头像，使用用户表中的头像
        teamMember.setAvatar(userAvatar != null ? userAvatar : "/imgs/default-avatar.jpg");
        teamMember.setIsLeader(0); // 设置为普通成员
        teamMember.setStatus(1); // 直接设置为已加入状态
        teamMember.setJoinTime(LocalDateTime.now()); // 设置加入时间

        // 保存队伍成员信息
        boolean saved = save(teamMember);
        if (!saved) {
            return Result.fail("加入队伍失败");
        }

        // 更新队伍当前人数
        team.setCurrentMembers(team.getCurrentMembers() + 1);
        boolean updated = teamsService.updateById(team);
        if (!updated) {
            // 如果更新失败，回滚成员添加
            removeById(teamMember.getId());
            return Result.fail("更新队伍人数失败");
        }

        return Result.ok();
    }

    /**
     * 退出队伍
     * @param teamId 队伍ID
     * @param userId 用户ID
     * @return 是否成功
     */
    @Override
    @Transactional
    public boolean quitTeam(Long teamId, Long userId) {
        // 1. 查询用户是否在队伍中
        LambdaQueryWrapper<TeamMembers> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamMembers::getTeamId, teamId)
                .eq(TeamMembers::getUserId, userId);

        TeamMembers teamMember = teamMembersMapper.selectOne(queryWrapper);

        if (teamMember == null) {
            // 用户不在队伍中
            return false;
        }

        // 2. 检查是否为队长，队长不能直接退出队伍
        if (teamMember.getIsLeader()==1) {
            return false;
        }

        // 3. 删除成员记录
        int result = teamMembersMapper.delete(queryWrapper);

        if (result > 0) {
            // 4. 更新队伍当前成员数
            Teams team = teamsMapper.selectById(teamId);
            if (team != null) {
                team.setCurrentMembers(team.getCurrentMembers() - 1);
                teamsMapper.updateById(team);
            }
            return true;
        }

        return false;
    }


}
