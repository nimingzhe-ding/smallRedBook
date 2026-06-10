package com.hmdp.danmaku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.exception.BusinessException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class DanmakuWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ObjectMapper objectMapper;
    private final DanmakuRoomRegistry roomRegistry;
    private final DanmakuRealtimeService realtimeService;

    public DanmakuWebSocketFrameHandler(
            ObjectMapper objectMapper,
            DanmakuRoomRegistry roomRegistry,
            DanmakuRealtimeService realtimeService) {
        this.objectMapper = objectMapper;
        this.roomRegistry = roomRegistry;
        this.realtimeService = realtimeService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            return;
        }
        if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
            return;
        }
        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            sendError(ctx, 400, "unsupported websocket frame");
            return;
        }

        try {
            DanmakuInboundMessage message = objectMapper.readValue(textFrame.text(), DanmakuInboundMessage.class);
            realtimeService.handleIncoming(ctx.channel(), message);
        } catch (BusinessException e) {
            sendError(ctx, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Handle danmaku websocket frame failed", e);
            sendError(ctx, 400, "invalid danmaku message");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        roomRegistry.leave(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            Long videoId = ctx.channel().attr(DanmakuChannelAttrs.VIDEO_ID).get();
            if (videoId != null) {
                int roomSize = roomRegistry.join(videoId, ctx.channel());
                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(
                        Map.of("type", "connected", "videoId", videoId, "roomSize", roomSize)
                )));
            }
            return;
        }
        if (evt instanceof IdleStateEvent idleStateEvent && idleStateEvent.state() == IdleState.READER_IDLE) {
            sendError(ctx, 408, "heartbeat timeout");
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Danmaku websocket channel exception", cause);
        roomRegistry.leave(ctx.channel());
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        roomRegistry.leave(ctx.channel());
        super.handlerRemoved(ctx);
    }

    private void sendError(ChannelHandlerContext ctx, int code, String message) {
        try {
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(
                    Map.of("type", "error", "code", code, "message", message == null ? "" : message)
            )));
        } catch (Exception ignored) {
            ctx.close();
        }
    }
}
