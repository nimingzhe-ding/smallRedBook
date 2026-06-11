package com.hmdp.annotation;

import com.hmdp.enums.RateLimitScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SlidingWindowRateLimit {
    String key() default "";

    int maxRequests() default 60;

    int windowSeconds() default 60;

    RateLimitScope scope() default RateLimitScope.USER_OR_IP;

    String message() default "Too many requests, please retry later";
}
