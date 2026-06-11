package com.hmdp.config;

import com.hmdp.annotation.Idempotent;
import com.hmdp.utils.RedisConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;

@Slf4j
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    public IdempotencyInterceptor(StringRedisTemplate stringRedisTemplate, MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Idempotent idempotent = resolveAnnotation(handlerMethod);
        if (idempotent == null) {
            return true;
        }

        String requestKey = request.getHeader(idempotent.header());
        if (idempotent.requireKey() && !RequestKeySupport.hasText(requestKey)) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, 400,
                    "Missing " + idempotent.header());
            return false;
        }

        String businessKey = RequestKeySupport.hasText(idempotent.key())
                ? idempotent.key()
                : request.getMethod() + ":" + RequestKeySupport.handlerPattern(request);
        String fingerprint = RequestKeySupport.hasText(requestKey)
                ? "header:" + requestKey.trim()
                : fallbackFingerprint(request);
        String owner = RequestKeySupport.currentUserOrIp(request);
        String redisKey = RedisConstants.IDEMPOTENT_KEY + RequestKeySupport.sha256(businessKey + ":" + owner + ":" + fingerprint);
        int ttlSeconds = Math.max(1, idempotent.expireSeconds());
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, traceId(request), Duration.ofSeconds(ttlSeconds));

        if (Boolean.TRUE.equals(acquired)) {
            response.setHeader("X-Idempotency-Key", RequestKeySupport.sha256(fingerprint).substring(0, 16));
            return true;
        }

        Counter.builder("hmdp.idempotent.duplicates")
                .tag("key", RequestKeySupport.sanitize(businessKey))
                .register(meterRegistry)
                .increment();
        log.warn("Duplicate request rejected: key={}, owner={}, uri={}", businessKey, owner, request.getRequestURI());
        writeJson(response, HttpServletResponse.SC_CONFLICT, 1006, idempotent.message());
        return false;
    }

    private Idempotent resolveAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Idempotent methodIdempotent = AnnotationUtils.findAnnotation(method, Idempotent.class);
        if (methodIdempotent != null) {
            return methodIdempotent;
        }
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), Idempotent.class);
    }

    private String fallbackFingerprint(HttpServletRequest request) {
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        return request.getMethod() + ":" + RequestKeySupport.handlerPattern(request)
                + ":query=" + query
                + ":body=" + RequestKeySupport.bodyHash(request);
    }

    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader(RequestLoggingInterceptor.TRACE_ID_HEADER);
        return RequestKeySupport.hasText(traceId) ? traceId : "1";
    }

    private void writeJson(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"code\":" + code + ",\"errorMsg\":\"" + escape(message) + "\"}");
    }

    private String escape(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
