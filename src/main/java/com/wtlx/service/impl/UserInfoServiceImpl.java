package com.wtlx.service.impl;

import com.wtlx.entity.UserInfo;
import com.wtlx.mapper.UserInfoMapper;
import com.wtlx.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
