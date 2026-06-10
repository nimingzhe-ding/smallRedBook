package com.hmdp.livechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.LiveChatWebSocketProperties;
import com.hmdp.service.ILiveRoomService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LiveChatChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final LiveChatWebSocketProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ILiveRoomService liveRoomService;
    private final ObjectMapper objectMapper;
    private final LiveChatRoomRegistry roomRegistry;
    private final LiveChatRealtimeService realtimeService;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(8192))
                .addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS))
                .addLast(new LiveChatHandshakeAuthHandler(properties, stringRedisTemplate, liveRoomService))
                .addLast(new WebSocketServerProtocolHandler(
                        properties.getPath(),
                        null,
                        true,
                        properties.getMaxFramePayloadLength()
                ))
                .addLast(new LiveChatWebSocketFrameHandler(objectMapper, roomRegistry, realtimeService));
    }
}
