package com.hmdp.privatemessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.SlidingWindowRateLimiter;
import com.hmdp.dto.PrivateMessageRequest;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.IPrivateMessageService;
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
public class PrivateMessageWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ObjectMapper objectMapper;
    private final PrivateMessageConnectionRegistry connectionRegistry;
    private final IPrivateMessageService privateMessageService;
    private final SlidingWindowRateLimiter rateLimiter;

    public PrivateMessageWebSocketFrameHandler(
            ObjectMapper objectMapper,
            PrivateMessageConnectionRegistry connectionRegistry,
            IPrivateMessageService privateMessageService,
            SlidingWindowRateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.privateMessageService = privateMessageService;
        this.rateLimiter = rateLimiter;
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
        UserDTO user = ctx.channel().attr(PrivateMessageChannelAttrs.USER).get();
        if (user == null || user.getId() == null) {
            sendError(ctx, 401, "login required");
            ctx.close();
            return;
        }
        long count = rateLimiter.hit("ws:private-message:send:u:" + user.getId(), 120, 60);
        if (count > 120) {
            sendError(ctx, 429, "Too many private messages, please retry later");
            return;
        }
        try {
            PrivateMessageInboundMessage inbound =
                    objectMapper.readValue(textFrame.text(), PrivateMessageInboundMessage.class);
            PrivateMessageRequest request = new PrivateMessageRequest();
            request.setRequestId(inbound.getRequestId());
            request.setContent(inbound.getContent());
            privateMessageService.sendMessageFromUser(inbound.getConversationId(), user.getId(), request);
        } catch (BusinessException e) {
            sendError(ctx, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Handle private message websocket frame failed", e);
            sendError(ctx, 400, "invalid private message");
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            UserDTO user = ctx.channel().attr(PrivateMessageChannelAttrs.USER).get();
            if (user != null && user.getId() != null) {
                int connections = connectionRegistry.bind(user.getId(), ctx.channel());
                ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(
                        Map.of("type", "connected", "userId", user.getId(), "connections", connections)
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connectionRegistry.unbind(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        connectionRegistry.unbind(ctx.channel());
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Private message websocket channel exception", cause);
        connectionRegistry.unbind(ctx.channel());
        ctx.close();
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
