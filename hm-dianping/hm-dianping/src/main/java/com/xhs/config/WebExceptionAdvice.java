package com.xhs.config;

import com.aliyun.oss.OSSException;
import com.xhs.dto.Result;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.exception.ForbiddenException;
import com.xhs.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return new Result(false, e.getCode(), e.getMessage(), null, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Result handleUnauthorizedException(UnauthorizedException e) {
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public Result handleForbiddenException(ForbiddenException e) {
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result handleValidationException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex) {
            var fieldError = ex.getBindingResult().getFieldError();
            if (fieldError != null) {
                message = fieldError.getDefaultMessage();
            }
        } else if (e instanceof BindException ex) {
            var fieldError = ex.getBindingResult().getFieldError();
            if (fieldError != null) {
                message = fieldError.getDefaultMessage();
            }
        }
        return new Result(false, ErrorCode.BAD_REQUEST.getCode(), message, null, null);
    }

    @ExceptionHandler(OSSException.class)
    public Result handleOssException(OSSException e) {
        if ("AccessDenied".equalsIgnoreCase(e.getErrorCode())) {
            log.warn("OSS access denied: action may be missing permission, requestId={}", e.getRequestId());
            return Result.fail(ErrorCode.UPLOAD_FAIL, "OSS 上传被拒绝，请检查 RAM 用户是否已授权 oss:PutObject 到当前 Bucket");
        }
        log.error("OSS upload exception", e);
        return Result.fail(ErrorCode.UPLOAD_FAIL, "OSS 上传失败，请检查 Bucket、Endpoint、AccessKey 和网络配置");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("服务器异常", e);
        return new Result(false, ErrorCode.SERVER_BUSY.getCode(), "服务器异常", null, null);
    }
}
