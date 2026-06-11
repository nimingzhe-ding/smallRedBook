package com.xhs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    String key() default "";

    int expireSeconds() default 30;

    boolean requireKey() default false;

    String header() default "Idempotency-Key";

    String message() default "Duplicate request, please do not submit repeatedly";
}
