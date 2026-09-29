package com.wtlx.controller;


import cn.hutool.core.bean.BeanUtil;
import com.wtlx.dto.LoginFormDTO;
import com.wtlx.dto.Result;
import com.wtlx.dto.UserDTO;
import com.wtlx.dto.UserUpdateDTO;
import com.wtlx.entity.User;
import com.wtlx.entity.UserInfo;
import com.wtlx.service.IUserInfoService;
import com.wtlx.service.IUserService;
import com.wtlx.utils.UserHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-3-26
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Api(tags = "用户模块接口")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // 实现登出功能,已在拦截器中实现，remove掉用户的线程信息
        return Result.ok();
    }

    /**
     * 校验用户的登录状态
     * @return
     */
    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        Long userId = UserHolder.getUser().getId();
        UserDTO userDTO = userService.getUserById(userId);
        return Result.ok(userDTO);
    }

    /**
     * 查询用户信息
     * @param userId
     * @return
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 根据id查询用户
     * @param userId
     * @return
     */
    @ApiOperation("根据id查询用户")
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 签到
     * @return
     */
    @ApiOperation("签到")
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 计算连续签到天数
     * @return
     */
    @ApiOperation("计算连续敲到天数")
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }


    /**
     * 修改用户信息
     * @param userUpdateDTO
     * @return
     */
    @PutMapping("/update")
    @ApiOperation("修改用户信息")
    public Result updateUserInfo(@RequestBody UserUpdateDTO userUpdateDTO){
        return userService.updateUserInfo(userUpdateDTO);
    }

}
