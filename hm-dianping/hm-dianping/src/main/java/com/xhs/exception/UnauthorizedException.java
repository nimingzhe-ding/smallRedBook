package com.xhs.exception;

import com.xhs.enums.ErrorCode;

/**
 * 未登录异常，对应 HTTP 401。
 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED);
    }
}
