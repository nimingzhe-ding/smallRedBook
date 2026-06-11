package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.Result;
import com.xhs.entity.VideoDanmaku;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 视频弹幕服务。
 */
public interface IVideoDanmakuService extends IService<VideoDanmaku> {
    Result listByBlog(Long blogId);

    Result send(VideoDanmaku danmaku);

    Result report(Long danmakuId);

    SseEmitter stream(Long blogId);

    boolean canUseDanmaku(Long blogId);
}
