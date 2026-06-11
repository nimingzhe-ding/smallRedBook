package com.xhs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.entity.CompensationEvent;

import java.time.LocalDateTime;

public interface CompensationEventService extends IService<CompensationEvent> {

    void record(String eventType, String bizType, String bizId, String eventKey, Object payload, String errorMessage);

    void record(String eventType, String bizType, String bizId, String eventKey, Object payload,
                String errorMessage, LocalDateTime nextRetryTime, Integer maxRetry);

    void cancelByKey(String eventKey, String reason);

    boolean replayNow(Long eventId);

    int replayDue(Integer limit);

    Page<CompensationEvent> pageEvents(String status, String eventType, Integer current, Integer size);
}
