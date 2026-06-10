package com.hmdp.livechat;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.config.LiveChatWebSocketProperties;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.ILiveRoomService;
import com.hmdp.utils.RedisConstants;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LiveChatHandshakeAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final LiveChatWebSocketProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ILiveRoomService liveRoomService;

    public LiveChatHandshakeAuthHandler(
            LiveChatWebSocketProperties properties,
            StringRedisTemplate stringRedisTemplate,
            ILiveRoomService liveRoomService) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.liveRoomService = liveRoomService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        if (!properties.getPath().equals(decoder.path())) {
            reject(ctx, HttpResponseStatus.NOT_FOUND, "unsupported websocket path");
            return;
        }

        Long roomId = parseRoomId(decoder.parameters());
        if (roomId == null || !liveRoomService.canChat(roomId)) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "live room does not exist or is not living");
            return;
        }

        UserDTO user = authenticate(request, decoder.parameters());
        if (user == null) {
            reject(ctx, HttpResponseStatus.UNAUTHORIZED, "login required");
            return;
        }

        ctx.channel().attr(LiveChatChannelAttrs.ROOM_ID).set(roomId);
        ctx.channel().attr(LiveChatChannelAttrs.USER).set(user);
        ctx.fireChannelRead(request.retain());
    }

    private Long parseRoomId(Map<String, List<String>> parameters) {
        String raw = first(parameters, "roomId");
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserDTO authenticate(FullHttpRequest request, Map<String, List<String>> parameters) {
        String token = first(parameters, "token");
        if (StrUtil.isBlank(token)) {
            token = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        }
        token = normalizeToken(token);
        if (StrUtil.isBlank(token)) {
            return null;
        }

        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            return null;
        }
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    }

    private String normalizeToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        String value = token.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String first(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private void reject(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        byte[] bytes = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
