package com.hmdp.livechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.exception.BusinessException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class LiveChatWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ObjectMapper objectMapper;
    private final LiveChatRoomRegistry roomRegistry;
    private final LiveChatRealtimeService realtimeService;

    public LiveChatWebSocketFrameHandler(
            ObjectMapper objectMapper,
            LiveChatRoomRegistry roomRegistry,
            LiveChatRealtimeService realtimeService) {
        this.objectMapper = objectMapper;
        this.roomRegistry = roomRegistry;
        this.realtimeService = realtimeService;
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
            LiveChatInboundMessage message = objectMapper.readValue(textFrame.text(), LiveChatInboundMessage.class);
            realtimeService.handleIncoming(ctx.channel(), message);
        } catch (BusinessException e) {
            sendError(ctx, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Handle live chat websocket frame failed", e);
            sendError(ctx, 400, "invalid live chat message");
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
            Long roomId = ctx.channel().attr(LiveChatChannelAttrs.ROOM_ID).get();
            if (roomId != null) {
                int roomSize = roomRegistry.join(roomId, ctx.channel());
                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(
                        Map.of("type", "connected", "roomId", roomId, "roomSize", roomSize)
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
        log.warn("Live chat websocket channel exception", cause);
        roomRegistry.leave(ctx.channel());
        ctx.close();
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
