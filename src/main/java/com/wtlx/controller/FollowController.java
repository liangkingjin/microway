package com.wtlx.controller;


import com.wtlx.domain.entity.dto.Result;
import com.wtlx.service.IFollowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-3-26
 */
@Api(tags = "好友关注模块")
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    /**
     * 关注或取关
     * @param followId  关注或者取关的人
     * @param isFollow  关注状态
     * @return
     */
    @ApiOperation("关注或取关")
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followId, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followId, isFollow);
    }

    /**
     * 查看是否关注
     * @param followId
     * @return
     */
    @ApiOperation("查看是否关注")
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followId){
        return followService.isFollow(followId);
    }

    /**
     * 查询用户的共同关注
     * @param id
     * @return
     */
    @ApiOperation("查询用户的共同关注")
    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }

}
