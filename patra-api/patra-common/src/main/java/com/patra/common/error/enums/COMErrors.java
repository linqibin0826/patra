package com.patra.common.error.enums;

import com.patra.common.error.core.ErrorCode;
import com.patra.common.error.core.ErrorDef;

/**
 * 平台通用错误（COM-*）。只绑定 code，其它信息来自 codebook。
 */
public enum COMErrors implements ErrorDef {

    // === NETWORK (N) ===
    CONNECT_TIMEOUT("COM-N0001"),
    READ_TIMEOUT("COM-N0002"),
    CIRCUIT_BREAKER_OPEN("COM-N0401"),
    RATE_LIMITED("COM-N0601"),

    // === SERVER (S) ===
    UNEXPECTED_SERVER_ERROR("COM-S0001"),
    DATABASE_ACCESS_ERROR("COM-S0301"),

    // === CLIENT (C) ===
    MISSING_OR_INVALID_PARAMETER("COM-C0101"),
    VALIDATION_FAILED("COM-C0201"),
    UNAUTHORIZED("COM-C2001"),
    FORBIDDEN("COM-C2101"),
    RESOURCE_NOT_FOUND("COM-C0301"),

    // === BUSINESS (B) ===
    VERSION_CONFLICT("COM-B0101"),

    // === UNKNOWN (U) ===
    UNCLASSIFIED_ERROR("COM-U0001");

    private final ErrorCode code;

    COMErrors(String literal) {
        this.code = ErrorCode.of(literal);
    }

    @Override
    public ErrorCode code() {
        return code;
    }
}
