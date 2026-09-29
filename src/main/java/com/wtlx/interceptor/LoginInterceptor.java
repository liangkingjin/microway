package com.wtlx.interceptor;

import com.wtlx.dto.UserDTO;
import com.wtlx.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO userDTO = UserHolder.getUser();
        //3.判断用户是否存在
        if(userDTO==null){
            response.setStatus(401);
            //拦截
            return false;
        }
        //有用户，放行
        return true;
    }
}
