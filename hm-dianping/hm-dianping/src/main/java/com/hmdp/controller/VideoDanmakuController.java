package com.hmdp.controller;

import com.hmdp.annotation.Idempotent;
import com.hmdp.annotation.SlidingWindowRateLimit;
import com.hmdp.dto.Result;
import com.hmdp.entity.VideoDanmaku;
import com.hmdp.enums.RateLimitScope;
import com.hmdp.service.IVideoDanmakuService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 视频弹幕接口。
 * 弹幕读取允许匿名访问，发送弹幕由登录拦截器保护。
 */
@RestController
@RequestMapping("/video-danmaku")
public class VideoDanmakuController {

    @Resource
    private IVideoDanmakuService danmakuService;

    @GetMapping("/public/{blogId}")
    public Result list(@PathVariable("blogId") Long blogId) {
        return danmakuService.listByBlog(blogId);
    }

    @PostMapping
    @SlidingWindowRateLimit(key = "video:danmaku:send", maxRequests = 60, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "video:danmaku:send", expireSeconds = 3)
    public Result send(@RequestBody VideoDanmaku danmaku) {
        return danmakuService.send(danmaku);
    }

    @PutMapping("/{id}/report")
    @SlidingWindowRateLimit(key = "video:danmaku:report", maxRequests = 20, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "video:danmaku:report", expireSeconds = 30)
    public Result report(@PathVariable("id") Long id) {
        return danmakuService.report(id);
    }

    @GetMapping("/stream/{blogId}")
    public SseEmitter stream(@PathVariable("blogId") Long blogId) {
        return danmakuService.stream(blogId);
    }
}
