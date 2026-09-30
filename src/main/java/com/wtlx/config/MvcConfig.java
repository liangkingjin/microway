package com.wtlx.config;

import com.wtlx.interceptor.LoginInterceptor;
import com.wtlx.interceptor.RefreshTokenInterceptor;
import com.wtlx.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.*;


@Configuration
@Slf4j
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("正在注册登录拦截器：{}",registry);
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/imgs/**",
                        "/video/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "//provinces/**",
                        "/guides/**",
                        "/scenic-spots/**",
                        "/ai/**"
                ).excludePathPatterns("/doc.html").excludePathPatterns("/webjars/**").order(1);
        //token刷新的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }

    /**
     * 静态资源映射：上传的图片/视频写入 nginx 托管目录，生产由 nginx 直出；
     * 开发环境（npm run dev）没有 nginx，改由后端直接托管，前端 vite 将 /imgs、/video 代理到本服务，
     * 保证"上传后能预览、发现页能播放"不依赖 nginx 是否启动。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String root = SystemConstants.STATIC_ROOT.replace("\\", "/");
        registry.addResourceHandler("/imgs/**").addResourceLocations("file:" + root + "/imgs/");
        registry.addResourceHandler("/video/**").addResourceLocations("file:" + root + "/video/");
    }
}
