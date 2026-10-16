package com.wtlx.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.wtlx.domain.entity.dto.UserDTO;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//token刷新的拦截器
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取token：优先请求头，其次查询参数（SSE 专用，见 resolveToken 说明）
        String token = resolveToken(request);
        if(StrUtil.isBlank(token)){
            return true;
        }
        //2.基于token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if (userMap.isEmpty()){
            return true;
        }
        //4.将查询到的hash数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //5.将用户信息保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        //6.刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        //7.放行
        return true;
    }

    /**
     * 取登录令牌：优先 {@code authorization} 请求头，为空再退到查询参数 {@code token}。
     * <p>
     * 查询参数这一路<b>只为 SSE 服务</b>：浏览器原生 {@code EventSource} 无法设置任何请求头，
     * 而 AI 流式对话正是建立在它之上。这不是"身份降级"——两种通道拿到的是同一个 token，
     * 后续 Redis 校验、UserDTO 还原、TTL 刷新完全一致，没有任何分支差异。
     * 真正决定"是否登录"的是 {@link LoginInterceptor}，与本方法无关。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("authorization");
        return StrUtil.isNotBlank(header) ? header : request.getParameter("token");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
