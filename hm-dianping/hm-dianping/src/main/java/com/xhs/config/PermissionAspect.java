package com.xhs.config;

import com.xhs.annotation.RequireRole;
import com.xhs.dto.UserDTO;
import com.xhs.enums.ErrorCode;
import com.xhs.enums.UserRole;
import com.xhs.exception.BusinessException;
import com.xhs.exception.UnauthorizedException;
import com.xhs.utils.UserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限校验切面：拦截 @RequireRole 注解的方法。
 */
@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(com.xhs.annotation.RequireRole) || @within(com.xhs.annotation.RequireRole)")
    public Object checkPermission(ProceedingJoinPoint pjp) throws Throwable {
        RequireRole requireRole = resolveRequireRole(pjp);
        UserRole required = requireRole.value();
        UserDTO user = UserHolder.getUser();

        if (required == UserRole.GUEST) {
            return pjp.proceed();
        }

        if (user == null) {
            throw new UnauthorizedException();
        }

        Integer roleCode = user.getRole();
        UserRole currentRole = UserRole.of(roleCode == null ? UserRole.USER.getCode() : roleCode);
        if (!currentRole.isAtLeast(required)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "需要" + required.getDesc() + "权限");
        }

        return pjp.proceed();
    }

    private RequireRole resolveRequireRole(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RequireRole methodRole = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (methodRole != null) {
            return methodRole;
        }
        return AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), RequireRole.class);
    }
}
