package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    @Resource
    private IdempotencyInterceptor idempotencyInterceptor;

    @Value("${hmdp.upload.image-dir}")
    private String imageUploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/**")
                .order(-2);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(-1);

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        "/error",
                        "/imgs/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/voucher/**",
                        "/notes/feed",
                        "/notes/search",
                        "/notes/trends",
                        "/notes/suggestions",
                        "/notes/*",
                        "/notes/user/**",
                        "/notes/comments/of/note",
                        "/notes/comments/of/blog",
                        "/profiles/*",
                        "/ai/flow/search",
                        "/ai/flow/shopping-guide",
                        "/ai/flow/note-summary",
                        "/ai/flow/customer-service",
                        "/ai/customer-service/chat",
                        "/mall/products/**",
                        "/note-event",
                        "/video-danmaku/public/**",
                        "/video-danmaku/stream/**",
                        "/notifications/stream"
                )
                .addPathPatterns("/**")
                .order(1);

        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/**")
                .order(2);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imageRoot = Paths.get(imageUploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations(imageRoot.toUri().toString() + "/");
    }
}
