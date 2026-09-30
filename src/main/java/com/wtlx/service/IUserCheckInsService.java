package com.wtlx.service;

import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.UserCheckIns;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 金小梁
 * @since 2025-03-14
 */
public interface IUserCheckInsService extends IService<UserCheckIns> {

    /**
     * 查询用户签到信息
     * @return
     */
    Result queryCheckIn();

    /**
     * 打卡
     * @return
     */
    Result queryCheckInData();

}
