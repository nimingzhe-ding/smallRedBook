package com.xhs.exception;

import com.xhs.enums.ErrorCode;

/**
 * 无权限异常，对应 HTTP 403。
 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }
}
