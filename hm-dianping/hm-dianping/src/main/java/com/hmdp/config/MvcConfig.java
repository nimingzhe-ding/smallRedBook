package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RequestLoggingInterceptor requestLoggingInterceptor;
    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Value("${hmdp.upload.image-dir}")
    private String imageUploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求日志拦截器（最先执行，记录所有请求）
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/**").order(-1);
        // 限流拦截器：对登录/验证码/上传接口做频率限制
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/user/code", "/user/login", "/upload/**").order(0);
        // 登录拦截器：除公开接口外，其余接口需要登录态
        registry.addInterceptor(new LoginInterceptor()).
                excludePathPatterns(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        "/error",
                        "/imgs/**",
                        // 用户登录相关
                        "/user/code",
                        "/user/login",
                        // 店铺浏览（公开）
                        "/shop/**",
                        // 优惠券浏览（公开）
                        "/voucher/**",
                        // 笔记 feed、搜索、趋势、联想（公开浏览）
                        "/notes/feed",
                        "/notes/search",
                        "/notes/trends",
                        "/notes/suggestions",
                        "/notes/*",
                        "/notes/user/**",
                        "/notes/comments/of/note",
                        "/notes/comments/of/blog",
                        // 个人主页查看（公开）
                        "/profiles/*",
                        // AI 智能体流式接口（公开）
                        "/ai/flow/search",
                        "/ai/flow/shopping-guide",
                        "/ai/flow/note-summary",
                        "/ai/flow/customer-service",
                        "/ai/customer-service/chat",
                        // 商城商品浏览（公开）
                        "/mall/products/**",
                        // 行为采集（公开，匿名也可上报）
                        "/note-event",
                        // 视频弹幕公开接口
                        "/video-danmaku/public/**",
                        "/video-danmaku/stream/**",
                        // 通知 SSE 通过 token query 参数单独鉴权
                        "/notifications/stream"
                ).addPathPatterns("/**").order(1);
        // Token 刷新拦截器：自动续期登录态
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imageRoot = Paths.get(imageUploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations(imageRoot.toUri().toString() + "/");
    }

}
