package com.xhs.controller;

import com.xhs.dto.Result;
import com.xhs.entity.VideoPlayMetric;
import com.xhs.service.IVideoPlayMetricService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频播放指标采集接口。
 */
@RestController
@RequestMapping("/video")
public class VideoMetricController {

    @Resource
    private IVideoPlayMetricService videoPlayMetricService;

    @PostMapping("/metrics/play")
    public Result reportPlay(@RequestBody VideoPlayMetric metric) {
        return videoPlayMetricService.report(metric);
    }
}
