package com.xhs.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachedBodyFilter extends OncePerRequestFilter {

    private static final Set<String> CACHE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldCache(request)) {
            filterChain.doFilter(new CachedBodyHttpServletRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldCache(HttpServletRequest request) {
        if (!CACHE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }
}
