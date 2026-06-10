package com.hmdp.danmaku;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuRoomRegistry {

    private final ObjectMapper objectMapper;
    private final Map<Long, ChannelGroup> rooms = new ConcurrentHashMap<>();

    public int join(Long videoId, Channel channel) {
        ChannelGroup group = rooms.computeIfAbsent(videoId,
                key -> new DefaultChannelGroup("danmaku-room-" + key, GlobalEventExecutor.INSTANCE));
        group.add(channel);
        channel.attr(DanmakuChannelAttrs.VIDEO_ID).set(videoId);
        return group.size();
    }

    public void leave(Channel channel) {
        Long videoId = channel.attr(DanmakuChannelAttrs.VIDEO_ID).get();
        if (videoId == null) {
            return;
        }
        ChannelGroup group = rooms.get(videoId);
        if (group == null) {
            return;
        }
        group.remove(channel);
        if (group.isEmpty()) {
            rooms.remove(videoId, group);
        }
    }

    public void broadcast(Long videoId, DanmakuEvent event) {
        ChannelGroup group = rooms.get(videoId);
        if (group == null || group.isEmpty()) {
            return;
        }
        try {
            group.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            log.warn("Serialize danmaku event failed, messageId={}", event.getMessageId(), e);
        }
    }
}
