package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.VideoDanmaku;
import com.hmdp.enums.ContentType;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.VideoDanmakuMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IVideoDanmakuService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频弹幕领域服务。
 * 弹幕读取允许匿名访问，发送弹幕要求用户已登录且笔记支持视频互动。
 */
@Service
public class VideoDanmakuServiceImpl extends ServiceImpl<VideoDanmakuMapper, VideoDanmaku> implements IVideoDanmakuService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Resource
    private IBlogService blogService;

    @Override
    public Result listByBlog(Long blogId) {
        if (blogId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "视频ID不能为空");
        }
        if (!canUseDanmaku(blogId)) {
            return Result.ok(List.of());
        }
        List<Map<String, Object>> list = query()
                .eq("blog_id", blogId)
                .eq("status", false)
                .orderByAsc("video_second")
                .last("LIMIT 300")
                .list()
                .stream()
                .map(this::toView)
                .toList();
        return Result.ok(list);
    }

    @Override
    public Result send(VideoDanmaku danmaku) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (danmaku == null || danmaku.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "视频ID不能为空");
        }
        if (StrUtil.isBlank(danmaku.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "弹幕内容不能为空");
        }
        if (!canUseDanmaku(danmaku.getBlogId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有视频和直播内容可以发送弹幕");
        }

        // 保存用户 ID 便于后续审核和治理，但公开响应不暴露发送人。
        danmaku.setId(null);
        danmaku.setUserId(user.getId());
        danmaku.setContent(StrUtil.sub(danmaku.getContent().trim(), 0, 40));
        danmaku.setVideoSecond(Math.max(0, danmaku.getVideoSecond() == null ? 0 : danmaku.getVideoSecond()));
        danmaku.setLane(danmaku.getLane() == null ? null : Math.floorMod(danmaku.getLane(), 5));
        danmaku.setStatus(false);
        save(danmaku);
        Map<String, Object> view = toView(danmaku);
        broadcast(danmaku.getBlogId(), view);
        return Result.ok(view);
    }

    @Override
    public SseEmitter stream(Long blogId) {
        if (blogId == null || !canUseDanmaku(blogId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频不存在或不支持弹幕");
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.computeIfAbsent(blogId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(blogId, emitter));
        emitter.onTimeout(() -> removeEmitter(blogId, emitter));
        emitter.onError(error -> removeEmitter(blogId, emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("blogId", blogId, "type", "connected")));
        } catch (Exception e) {
            removeEmitter(blogId, emitter);
        }
        return emitter;
    }

    private boolean canUseDanmaku(Long blogId) {
        Blog blog = blogService.getById(blogId);
        return blog != null && ContentType.supportsDanmaku(blog.getContentType(), blog.getVideoUrl());
    }

    private Map<String, Object> toView(VideoDanmaku danmaku) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", danmaku.getId());
        view.put("blogId", danmaku.getBlogId());
        view.put("content", danmaku.getContent());
        view.put("videoSecond", danmaku.getVideoSecond() == null ? 0 : danmaku.getVideoSecond());
        view.put("lane", danmaku.getLane() == null ? 0 : danmaku.getLane());
        return view;
    }

    private void broadcast(Long blogId, Map<String, Object> view) {
        Set<SseEmitter> blogEmitters = emitters.get(blogId);
        if (blogEmitters == null || blogEmitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(blogEmitters)) {
            try {
                emitter.send(SseEmitter.event().name("danmaku").data(view));
            } catch (Exception e) {
                removeEmitter(blogId, emitter);
            }
        }
    }

    private void removeEmitter(Long blogId, SseEmitter emitter) {
        Set<SseEmitter> blogEmitters = emitters.get(blogId);
        if (blogEmitters == null) {
            return;
        }
        blogEmitters.remove(emitter);
        if (blogEmitters.isEmpty()) {
            emitters.remove(blogId);
        }
    }
}
