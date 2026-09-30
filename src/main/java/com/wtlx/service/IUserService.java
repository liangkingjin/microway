package com.wtlx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wtlx.domain.entity.dto.LoginFormDTO;
import com.wtlx.domain.entity.dto.Result;
import com.wtlx.domain.entity.dto.UserDTO;
import com.wtlx.domain.entity.dto.UserUpdateDTO;
import com.wtlx.domain.entity.User;

import jakarta.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 签到
     * @return
     */
    Result sign();

    /**
     * 计算连续签到天数
     * @return
     */
    Result signCount();

    Result updateUserInfo(UserUpdateDTO userUpdateDTO);

    UserDTO getUserById(Long userId);
}
