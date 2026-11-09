package com.mall.common.exception;

import com.mall.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAIL.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
