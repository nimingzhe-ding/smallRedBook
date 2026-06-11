package com.hmdp.privatemessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.PrivateMessageWebSocketProperties;
import com.hmdp.config.SlidingWindowRateLimiter;
import com.hmdp.service.IPrivateMessageService;
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
public class PrivateMessageChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final PrivateMessageWebSocketProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PrivateMessageConnectionRegistry connectionRegistry;
    private final IPrivateMessageService privateMessageService;
    private final SlidingWindowRateLimiter rateLimiter;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(8192))
                .addLast(new IdleStateHandler(properties.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS))
                .addLast(new PrivateMessageHandshakeAuthHandler(properties, stringRedisTemplate))
                .addLast(new WebSocketServerProtocolHandler(
                        properties.getPath(),
                        null,
                        true,
                        properties.getMaxFramePayloadLength()
                ))
                .addLast(new PrivateMessageWebSocketFrameHandler(
                        objectMapper,
                        connectionRegistry,
                        privateMessageService,
                        rateLimiter
                ));
    }
}
