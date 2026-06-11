package com.xhs.privatemessage;

import com.xhs.config.PrivateMessageWebSocketProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateMessageNettyWebSocketServer {

    private final PrivateMessageWebSocketProperties properties;
    private final PrivateMessageChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() throws InterruptedException {
        if (!properties.isEnabled()) {
            log.info("Private message Netty websocket server is disabled");
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(channelInitializer);

        ChannelFuture future = bootstrap.bind(properties.getPort()).sync();
        serverChannel = future.channel();
        log.info("Private message Netty websocket server started at ws://0.0.0.0:{}{}",
                properties.getPort(), properties.getPath());
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Private message Netty websocket server stopped");
    }
}
