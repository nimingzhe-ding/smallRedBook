package com.xhs.danmaku;

import cn.hutool.core.util.StrUtil;
import com.xhs.config.RequestKeySupport;
import com.xhs.config.SlidingWindowRateLimiter;
import com.xhs.dto.UserDTO;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.service.ContentModerationService;
import com.xhs.utils.RedisConstants;
import com.xhs.utils.RedisIdWorker;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DanmakuRealtimeService {

    private static final int MAX_CONTENT_LENGTH = 40;
    private static final int LANE_COUNT = 5;

    private final ContentModerationService contentModerationService;
    private final RedisIdWorker redisIdWorker;
    private final DanmakuRoomRegistry roomRegistry;
    private final DanmakuKafkaProducer kafkaProducer;
    private final SlidingWindowRateLimiter rateLimiter;
    private final StringRedisTemplate stringRedisTemplate;

    public DanmakuEvent handleIncoming(Channel channel, DanmakuInboundMessage message) {
        Long videoId = channel.attr(DanmakuChannelAttrs.VIDEO_ID).get();
        UserDTO user = channel.attr(DanmakuChannelAttrs.USER).get();
        if (videoId == null || user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (message == null || StrUtil.isBlank(message.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "danmaku content must not be empty");
        }
        long count = rateLimiter.hit("ws:danmaku:send:u:" + user.getId() + ":v:" + videoId, 60, 60);
        if (count > 60) {
            throw new BusinessException(429, "Too many danmaku messages, please retry later");
        }
        ensureIdempotent(message, user.getId(), videoId);

        String content = StrUtil.sub(message.getContent().trim(), 0, MAX_CONTENT_LENGTH);
        contentModerationService.checkText("danmaku content", content);

        DanmakuEvent event = new DanmakuEvent()
                .setMessageId(redisIdWorker.nextId("danmaku"))
                .setRequestId(message.getRequestId())
                .setVideoId(videoId)
                .setBlogId(videoId)
                .setUserId(user.getId())
                .setContent(content)
                .setVideoSecond(Math.max(0, message.getVideoSecond() == null ? 0 : message.getVideoSecond()))
                .setLane(message.getLane() == null ? 0 : Math.floorMod(message.getLane(), LANE_COUNT))
                .setCreateTime(System.currentTimeMillis());

        roomRegistry.broadcast(videoId, event);
        kafkaProducer.sendAsync(event);
        return event;
    }

    private void ensureIdempotent(DanmakuInboundMessage message, Long userId, Long videoId) {
        if (message == null || StrUtil.isBlank(message.getRequestId())) {
            return;
        }
        String raw = "ws:danmaku:" + userId + ":" + videoId + ":" + message.getRequestId().trim();
        String key = RedisConstants.IDEMPOTENT_KEY + RequestKeySupport.sha256(raw);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.REPEAT_OPERATION, "Duplicate danmaku request");
        }
    }
}
