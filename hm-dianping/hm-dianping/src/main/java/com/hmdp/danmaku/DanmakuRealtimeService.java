package com.hmdp.danmaku;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.ContentModerationService;
import com.hmdp.utils.RedisIdWorker;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DanmakuRealtimeService {

    private static final int MAX_CONTENT_LENGTH = 40;
    private static final int LANE_COUNT = 5;

    private final ContentModerationService contentModerationService;
    private final RedisIdWorker redisIdWorker;
    private final DanmakuRoomRegistry roomRegistry;
    private final DanmakuKafkaProducer kafkaProducer;

    public DanmakuEvent handleIncoming(Channel channel, DanmakuInboundMessage message) {
        Long videoId = channel.attr(DanmakuChannelAttrs.VIDEO_ID).get();
        UserDTO user = channel.attr(DanmakuChannelAttrs.USER).get();
        if (videoId == null || user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (message == null || StrUtil.isBlank(message.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "danmaku content must not be empty");
        }

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
}
