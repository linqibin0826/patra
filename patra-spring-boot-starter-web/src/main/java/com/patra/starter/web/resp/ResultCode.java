package com.patra.starter.web.resp;

import lombok.Getter;

/**
 * Representative Web-layer result codes used by the shared API response helpers.
 */
@Getter
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

    /** Numeric code exposed to clients. */
    private final int code;

    /** Human-readable description associated with the code. */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
