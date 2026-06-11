package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.Result;
import com.xhs.entity.VideoPlayMetric;

public interface IVideoPlayMetricService extends IService<VideoPlayMetric> {
    Result report(VideoPlayMetric metric);
}
