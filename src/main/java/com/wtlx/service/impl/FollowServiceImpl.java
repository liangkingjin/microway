package com.wtlx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.dto.Result;
import com.wtlx.dto.UserDTO;
import com.wtlx.entity.Follow;
import com.wtlx.mapper.FollowMapper;
import com.wtlx.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.service.IUserService;
import com.wtlx.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    /**
     * 关注或取关
     * @param followId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followId, Boolean isFollow) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        //2.判断当前登录用户是否已经关注了该用户
        if(isFollow){
            //2.1如果是关注
            Follow follow = Follow.builder()
                    .userId(userId)
                    .followUserId(followId)
                    .build();
            //插入到数据库中
            boolean isSuccess = save(follow);
            if(isSuccess){
                //将关注的用户放在redis中的set集合中
                stringRedisTemplate.opsForSet().add(key,followId.toString());
            }
        }else{
            //2.2如果是取关
            //删除数据 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followId)
            );
            if (isSuccess){
                //把关注用户的id从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key,followId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查看是否关注
     * @param followId
     * @return
     */
    @Override
    public Result isFollow(Long followId) {
        //1.获取当前用户的id
        Long userId = UserHolder.getUser().getId();
        //2.根据当前用户的id和需要查看是否关注的id来在表中进行查询
        Long count = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followId)
                .count();
        //3.判断是否关注了
        return Result.ok(count>0);
    }

    /**
     * 展示当前用户与博主之间的共同好友
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1.构造查询id
        Long userId = UserHolder.getUser().getId();
        String key1="follows:"+userId;
        String key2="follows:"+id;
        //2.求出两个人共同的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect==null||intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
