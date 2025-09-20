package com.patra.starter.web.resp;

import lombok.Getter;

@Getter
/**
 * Web 层通用结果码（示例）。
 */
public enum ResultCode {

    OK(0, "OK"),
    BAD_REQUEST(1400, "Bad Request"),
    VALIDATION_ERROR(1401, "Validation Failed"),
    UNAUTHORIZED(2401, "Unauthorized"),
    FORBIDDEN(2403, "Forbidden"),
    NOT_FOUND(1404, "Not Found"),
    METHOD_NOT_ALLOWED(1405, "Method Not Allowed"),
    CONFLICT(3409, "Conflict"),
    TOO_MANY_REQUESTS(1429, "Too Many Requests"),
    INTERNAL_ERROR(5500, "Internal Server Error"),
    SERVICE_UNAVAILABLE(5503, "Service Unavailable");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
