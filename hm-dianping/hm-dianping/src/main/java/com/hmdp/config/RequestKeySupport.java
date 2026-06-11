package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestKeySupport {

    private RequestKeySupport() {
    }

    public static String clientIp(HttpServletRequest request) {
        String ip = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (hasText(ip)) {
            return ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    public static String handlerPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null && hasText(pattern.toString())) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }

    public static String currentUserOrIp(HttpServletRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user != null && user.getId() != null) {
            return "u:" + user.getId();
        }
        return "ip:" + clientIp(request);
    }

    public static String currentUserAndIp(HttpServletRequest request) {
        UserDTO user = UserHolder.getUser();
        String userPart = user == null || user.getId() == null ? "guest" : String.valueOf(user.getId());
        return "u:" + userPart + ":ip:" + clientIp(request);
    }

    public static String bodyHash(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest cached) {
            byte[] body = cached.getCachedBody();
            if (body.length > 0) {
                return sha256(body);
            }
        }
        return "empty";
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String sanitize(String value) {
        if (!hasText(value)) {
            return "default";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstForwardedIp(String value) {
        if (!hasText(value) || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
