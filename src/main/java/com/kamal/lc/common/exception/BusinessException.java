package com.kamal.lc.common.exception;

import lombok.Getter;

/**
 * Custom business exception with error code and Arabic message support.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final String messageAr;

    public BusinessException(String errorCode, String messageAr) {
        super(messageAr);
        this.errorCode = errorCode;
        this.messageAr = messageAr;
    }

    public BusinessException(String errorCode, String messageAr, Throwable cause) {
        super(messageAr, cause);
        this.errorCode = errorCode;
        this.messageAr = messageAr;
    }
}
