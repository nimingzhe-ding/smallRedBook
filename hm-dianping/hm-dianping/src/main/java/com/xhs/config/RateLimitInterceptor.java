package com.xhs.config;

import com.xhs.annotation.SlidingWindowRateLimit;
import com.xhs.dto.UserDTO;
import com.xhs.enums.RateLimitScope;
import com.xhs.utils.UserHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final SlidingWindowRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public RateLimitInterceptor(SlidingWindowRateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        SlidingWindowRateLimit limit = resolveAnnotation(handlerMethod);
        if (limit == null) {
            return true;
        }

        int maxRequests = Math.max(1, limit.maxRequests());
        int windowSeconds = Math.max(1, limit.windowSeconds());
        String businessKey = RequestKeySupport.hasText(limit.key())
                ? limit.key()
                : request.getMethod() + ":" + RequestKeySupport.handlerPattern(request);
        String identity = identity(request, limit.scope());
        String rawKey = businessKey + ":" + limit.scope().name() + ":" + identity;
        long count = rateLimiter.hit(rawKey, maxRequests, windowSeconds);

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Window", String.valueOf(windowSeconds));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - count)));

        if (count <= maxRequests) {
            return true;
        }

        Counter.builder("xiaohongshu.rate.limit.rejected")
                .tag("key", RequestKeySupport.sanitize(businessKey))
                .tag("scope", limit.scope().name())
                .register(meterRegistry)
                .increment();
        log.warn("Rate limit rejected: key={}, scope={}, identity={}, count={}, limit={}, uri={}",
                businessKey, limit.scope(), identity, count, maxRequests, request.getRequestURI());
        writeJson(response, 429, 429, limit.message());
        return false;
    }

    private SlidingWindowRateLimit resolveAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        SlidingWindowRateLimit methodLimit = AnnotationUtils.findAnnotation(method, SlidingWindowRateLimit.class);
        if (methodLimit != null) {
            return methodLimit;
        }
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), SlidingWindowRateLimit.class);
    }

    private String identity(HttpServletRequest request, RateLimitScope scope) {
        return switch (scope) {
            case GLOBAL -> "global";
            case IP -> "ip:" + RequestKeySupport.clientIp(request);
            case USER -> {
                UserDTO user = UserHolder.getUser();
                yield user == null || user.getId() == null ? "guest" : "u:" + user.getId();
            }
            case USER_OR_IP -> RequestKeySupport.currentUserOrIp(request);
            case USER_AND_IP -> RequestKeySupport.currentUserAndIp(request);
        };
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
