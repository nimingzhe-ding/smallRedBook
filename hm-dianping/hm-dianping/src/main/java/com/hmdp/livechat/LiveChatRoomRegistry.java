package com.hmdp.livechat;

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
public class LiveChatRoomRegistry {

    private final ObjectMapper objectMapper;
    private final Map<Long, ChannelGroup> rooms = new ConcurrentHashMap<>();

    public int join(Long roomId, Channel channel) {
        ChannelGroup group = rooms.computeIfAbsent(roomId,
                key -> new DefaultChannelGroup("live-chat-room-" + key, GlobalEventExecutor.INSTANCE));
        group.add(channel);
        channel.attr(LiveChatChannelAttrs.ROOM_ID).set(roomId);
        return group.size();
    }

    public void leave(Channel channel) {
        Long roomId = channel.attr(LiveChatChannelAttrs.ROOM_ID).get();
        if (roomId == null) {
            return;
        }
        ChannelGroup group = rooms.get(roomId);
        if (group == null) {
            return;
        }
        group.remove(channel);
        if (group.isEmpty()) {
            rooms.remove(roomId, group);
        }
    }

    public void broadcast(Long roomId, LiveChatEvent event) {
        ChannelGroup group = rooms.get(roomId);
        if (group == null || group.isEmpty()) {
            return;
        }
        try {
            group.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            log.warn("Serialize live chat event failed, messageId={}", event.getMessageId(), e);
        }
    }
}
