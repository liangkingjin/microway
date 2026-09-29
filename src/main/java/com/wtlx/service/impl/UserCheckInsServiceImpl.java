package com.wtlx.service.impl;

import com.wtlx.dto.Result;
import com.wtlx.entity.CommonCityInfo;
import com.wtlx.entity.UserCheckIns;
import com.wtlx.mapper.UserCheckInsMapper;
import com.wtlx.service.ICommonCityInfoService;
import com.wtlx.service.IUserCheckInsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 金小梁
 * @since 2025-03-14
 */
@Service
public class UserCheckInsServiceImpl extends ServiceImpl<UserCheckInsMapper, UserCheckIns> implements IUserCheckInsService {
    @Autowired
    private ICommonCityInfoService commonCityInfoService;
    /**
     * 打卡发现
     * @return
     */
    @Override
    @Transactional
    public Result queryCheckIn() {
        Long userId = UserHolder.getUser().getId();
        UserCheckIns userCheckIns = query().eq("user_id", userId).one();
        //如果用户初次加载该模块，插入数据
        if(userCheckIns == null){
            //创建要插入的对象
            userCheckIns = new UserCheckIns();
            userCheckIns.setConsecutiveDays(1);
            CommonCityInfo commonCityInfo = commonCityInfoService.query().one();
            //组合用户信息
            String cityImages =commonCityInfo.getCityImages();
            String cityNames = commonCityInfo.getCityNames();
            userCheckIns.setCityName(cityNames);
            userCheckIns.setCityImage(cityImages);
            userCheckIns.setUserId(userId);
            userCheckIns.setHasCheckedIn(false);
            userCheckIns.setConsecutiveDays(1);
            //插入用户
            save(userCheckIns);
        }
        Integer days = userCheckIns.getConsecutiveDays();
        String[] imgs = userCheckIns.getCityImage().split(",");
        String[] citys = userCheckIns.getCityName().split(",");
        // 获取数组长度
        int imgLength = imgs.length;
        int cityLength = citys.length;

        // 使用取模运算符确保索引在数组长度范围内
        int currentIndex = (days - 1) % imgLength;
        int nextIndex = days % cityLength;

        userCheckIns.setCityName(citys[currentIndex]);
        userCheckIns.setCityImage(imgs[currentIndex]);
        userCheckIns.setNextCityName(citys[nextIndex]);
        update().eq("user_id", userId)
                .set("next_city_name", citys[nextIndex])
                .update();
        return Result.ok(userCheckIns);
    }

    /**
     * 签到
     * @return
     */
    @Override
    public Result queryCheckInData() {
        Long userId = UserHolder.getUser().getId();
        update().eq("user_id", userId)
                .set("has_checked_in", 1)
                //天数加1
                .setSql("consecutive_days = consecutive_days + 1")
                .update();
        return Result.ok();
    }
}
