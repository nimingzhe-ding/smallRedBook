package com.xhs.exception;

import com.xhs.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：携带统一错误码，由 WebExceptionAdvice 统一处理。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
