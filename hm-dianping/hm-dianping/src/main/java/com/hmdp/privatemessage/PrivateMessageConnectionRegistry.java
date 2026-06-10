package com.hmdp.privatemessage;

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
public class PrivateMessageConnectionRegistry {

    private final ObjectMapper objectMapper;
    private final Map<Long, ChannelGroup> userChannels = new ConcurrentHashMap<>();

    public int bind(Long userId, Channel channel) {
        ChannelGroup group = userChannels.computeIfAbsent(userId,
                key -> new DefaultChannelGroup("private-message-user-" + key, GlobalEventExecutor.INSTANCE));
        group.add(channel);
        return group.size();
    }

    public void unbind(Channel channel) {
        userChannels.forEach((userId, group) -> {
            group.remove(channel);
            if (group.isEmpty()) {
                userChannels.remove(userId, group);
            }
        });
    }

    public void push(Long userId, PrivateMessageWebSocketEvent event) {
        ChannelGroup group = userChannels.get(userId);
        if (group == null || group.isEmpty()) {
            return;
        }
        try {
            group.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            log.warn("Serialize private message websocket event failed, userId={}", userId, e);
        }
    }
}
