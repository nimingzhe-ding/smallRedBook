package com.xhs.danmaku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.config.DanmakuWebSocketProperties;
import com.xhs.service.IVideoDanmakuService;
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
public class DanmakuChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final DanmakuWebSocketProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final IVideoDanmakuService videoDanmakuService;
    private final ObjectMapper objectMapper;
    private final DanmakuRoomRegistry roomRegistry;
    private final DanmakuRealtimeService realtimeService;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(8192))
                .addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS))
                .addLast(new DanmakuHandshakeAuthHandler(properties, stringRedisTemplate, videoDanmakuService))
                .addLast(new WebSocketServerProtocolHandler(
                        properties.getPath(),
                        null,
                        true,
                        properties.getMaxFramePayloadLength()
                ))
                .addLast(new DanmakuWebSocketFrameHandler(objectMapper, roomRegistry, realtimeService));
    }
}
