package com.xhs.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger SLOW_LOG = LoggerFactory.getLogger("SLOW_API");
    private static final long SLOW_THRESHOLD_MS = 500;
    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String TRACE_ID_ATTR = "requestTraceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        request.setAttribute(TRACE_ID_ATTR, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        MDC.put("traceId", traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime == null ? 0 : System.currentTimeMillis() - startTime;
        Object traceId = request.getAttribute(TRACE_ID_ATTR);
        if (traceId != null) {
            MDC.put("traceId", traceId.toString());
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();
        int status = response.getStatus();
        String query = request.getQueryString();
        String queryPart = query != null ? " query=" + query : "";
        String logMsg = "{} {} {} status={} cost={}ms{}";

        try {
            if (duration > SLOW_THRESHOLD_MS) {
                SLOW_LOG.warn(logMsg, method, uri, request.getRemoteAddr(), status, duration, queryPart);
            } else {
                log.info(logMsg, method, uri, request.getRemoteAddr(), status, duration, queryPart);
            }
            if (ex != null) {
                log.error("Request failed: {} {}", method, uri, ex);
            }
        } finally {
            MDC.remove("traceId");
        }
    }
}
