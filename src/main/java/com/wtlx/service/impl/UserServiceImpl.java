package com.wtlx.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.entity.dto.LoginFormDTO;
import com.wtlx.entity.dto.Result;
import com.wtlx.entity.dto.UserDTO;
import com.wtlx.entity.dto.UserUpdateDTO;
import com.wtlx.entity.User;
import com.wtlx.entity.UserInfo;
import com.wtlx.mapper.UserMapper;
import com.wtlx.service.IUserInfoService;
import com.wtlx.service.IUserService;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.RegexUtils;
import com.wtlx.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.wtlx.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.wtlx.utils.RedisConstants.LOGIN_USER_TTL;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserInfoService userInfoService;


    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 发送短信验证码并保存验证码

        //1.校验手机号码
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.生成验证码
        String code= RandomUtil.randomNumbers(6);

        //3.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);

        return Result.ok(code);
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        //2.从redis中获取保存的验证码,校验验证码
        Object cacheCode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);

        if (cacheCode == null || !cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }

        //3.根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //4.判断用户是否存在
        if(user == null){
            //5.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        //6.保存用户到Redis中并返回token令牌
        //6.1随机生成token令牌，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //6.2 将User对象转为HashMap存储到Redis中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            // 如果字段值为 null，则返回空字符串或其他默认值
                            return fieldValue == null ? "" : fieldValue.toString();
                        }));

        //6.3 将HashMap存储到Redis中
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //6.4 设置token有效期为30分钟
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.SECONDS);

        //7.返回token
        return Result.ok(token);
    }

    /**
     * 签到
     * @return
     */
    @Override
    public Result sign() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key=RedisConstants.USER_SIGN_KEY+userId+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入Redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    /**
     * 计算连续签到天数
     * @return
     */
    @Override
    public Result signCount() {
        //1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key=RedisConstants.USER_SIGN_KEY+userId+keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，
        // 返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        //6.没有任何签到结果
        if(result==null||result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num==null||num==0){
            return Result.ok(0);
        }

        //7.循环遍历
        int count=0;
        while (true){
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if((num&1)==0){
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    @Transactional
    public Result updateUserInfo(UserUpdateDTO userUpdateDTO) {
        Long userId = UserHolder.getUser().getId();
        //更新用户表
        User user = BeanUtil.copyProperties(userUpdateDTO, User.class);
        if (BeanUtil.isNotEmpty(user)){
            user.setId(userId);
            updateById(user);
        }
        //更新用户信息表
        UserInfo userInfo = BeanUtil.copyProperties(userUpdateDTO, UserInfo.class);
        //判断userinfo的属性是否为空
        if(BeanUtil.isEmpty(userInfo)){
            return Result.ok();
        }
        userInfo.setUserId(userId);
        //查询用户是否有信息
        boolean one = userInfoService.query().eq("user_id", userId).one() == null;
        if(one){
            userInfoService.save(userInfo);
        }else {
            userInfoService.update().eq("user_id",userId).update(userInfo);
        }
        return Result.ok();
    }

    @Override
    public UserDTO getUserById(Long userId) {
        User user = query().eq("id", userId).one();
        UserInfo userInfo = userInfoService.query().eq("user_id", userId).one();
        UserDTO userDTO = new UserDTO();
        if(userInfo!=null){
            BeanUtils.copyProperties(userInfo,userDTO);
        }
        BeanUtils.copyProperties(user,userDTO);
        return userDTO;
    }

    /**
     * 创建新用户
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
