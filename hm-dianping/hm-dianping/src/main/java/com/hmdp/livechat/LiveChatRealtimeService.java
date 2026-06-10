package com.hmdp.livechat;

import cn.hutool.core.util.StrUtil;
import com.hmdp.config.LiveChatWebSocketProperties;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.LiveRoomMessage;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.LiveRoomMessageMapper;
import com.hmdp.service.ContentModerationService;
import com.hmdp.utils.RedisIdWorker;
import io.netty.channel.Channel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class LiveChatRealtimeService {

    private static final int MAX_CONTENT_LENGTH = 80;
    private static final Set<String> ALLOWED_TYPES = Set.of("danmaku", "chat", "system");

    private final ContentModerationService contentModerationService;
    private final RedisIdWorker redisIdWorker;
    private final LiveChatRoomRegistry roomRegistry;
    private final LiveRoomMessageMapper liveRoomMessageMapper;
    private final ExecutorService persistenceExecutor;

    public LiveChatRealtimeService(
            ContentModerationService contentModerationService,
            RedisIdWorker redisIdWorker,
            LiveChatRoomRegistry roomRegistry,
            LiveRoomMessageMapper liveRoomMessageMapper,
            LiveChatWebSocketProperties properties) {
        this.contentModerationService = contentModerationService;
        this.redisIdWorker = redisIdWorker;
        this.roomRegistry = roomRegistry;
        this.liveRoomMessageMapper = liveRoomMessageMapper;
        this.persistenceExecutor = Executors.newFixedThreadPool(Math.max(1, properties.getPersistenceThreads()));
    }

    public LiveChatEvent handleIncoming(Channel channel, LiveChatInboundMessage message) {
        Long roomId = channel.attr(LiveChatChannelAttrs.ROOM_ID).get();
        UserDTO user = channel.attr(LiveChatChannelAttrs.USER).get();
        if (roomId == null || user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (message == null || StrUtil.isBlank(message.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "message content must not be empty");
        }

        String content = StrUtil.sub(message.getContent().trim(), 0, MAX_CONTENT_LENGTH);
        contentModerationService.checkText("live chat content", content);

        LiveChatEvent event = new LiveChatEvent()
                .setMessageId(redisIdWorker.nextId("liveChat"))
                .setRequestId(message.getRequestId())
                .setRoomId(roomId)
                .setUserId(user.getId())
                .setType(resolveType(message.getType()))
                .setContent(content)
                .setLiked(0)
                .setStatus(0)
                .setCreateTime(System.currentTimeMillis());

        roomRegistry.broadcast(roomId, event);
        persistenceExecutor.execute(() -> persist(event));
        return event;
    }

    @PreDestroy
    public void shutdown() {
        persistenceExecutor.shutdown();
    }

    private String resolveType(String type) {
        String value = StrUtil.blankToDefault(type, "danmaku").trim().toLowerCase(Locale.ROOT);
        return ALLOWED_TYPES.contains(value) ? value : "danmaku";
    }

    private void persist(LiveChatEvent event) {
        try {
            LiveRoomMessage message = new LiveRoomMessage()
                    .setRoomId(event.getRoomId())
                    .setUserId(event.getUserId())
                    .setType(event.getType())
                    .setContent(event.getContent())
                    .setLiked(0)
                    .setStatus(0)
                    .setCreateTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(event.getCreateTime()),
                            ZoneId.systemDefault()
                    ));
            liveRoomMessageMapper.insert(message);
            event.setId(message.getId());
        } catch (Exception e) {
            log.error("Persist live chat message failed, messageId={}, roomId={}",
                    event.getMessageId(), event.getRoomId(), e);
        }
    }
}
