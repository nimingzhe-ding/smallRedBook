package com.xhs.annotation;

import com.xhs.enums.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解，可标注在 Controller 类或方法上。
 * 由 PermissionAspect 拦截校验。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /**
     * 最低角色要求，默认 USER。
     */
    UserRole value() default UserRole.USER;
}
