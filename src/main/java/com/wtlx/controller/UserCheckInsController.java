package com.wtlx.controller;


import com.wtlx.dto.Result;
import com.wtlx.service.IUserCheckInsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 金小梁
 * @since 2025-03-14
 */
@RestController
@RequestMapping("/user-check-ins")
@Api(tags = "打卡发现模块")
public class UserCheckInsController {
    @Autowired
    private IUserCheckInsService userCheckInsService;

    /**
     * 查询用户签到信息
     * @return
     */
    @GetMapping("/check-in-data")
    @ApiOperation("查询用户签到信息")
    public Result queryCheckIn() {
        return userCheckInsService.queryCheckIn();
    }

    /**
     * 打卡
     * @return
     */
    @GetMapping("/check-in")
    @ApiOperation("打卡")
    public Result CheckInData() {
        return userCheckInsService.queryCheckInData();
    }

}
