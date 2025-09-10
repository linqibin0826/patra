package com.patra.error.spec;

import com.patra.error.core.Category;
import com.patra.error.core.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台保留模块 COM 的通用错误码常量（跨系统共用）。
 * 只放“高频/基础”的码，业务模块延伸请在各自 *-api 的 codebook 中维护。
 */
public final class ComCodes {
    private ComCodes() {
    }

    /**
     * 便捷查询表（code → 说明）
     */
    private static final Map<String, String> LOOKUP;

    static {
        var m = new LinkedHashMap<String, String>();

        // NETWORK（N）
        m.put("COM-N0001", "Connect timeout");
        m.put("COM-N0002", "Read timeout");
        m.put("COM-N0003", "Connection refused");
        m.put("COM-N0004", "DNS resolution failure");
        m.put("COM-N0005", "TLS/Handshake/Hostname verification error");
        m.put("COM-N0401", "Circuit breaker open (call not permitted)");
        m.put("COM-N0501", "Bulkhead full (concurrency slots exhausted)");
        m.put("COM-N0601", "Rate limited (429)");
        m.put("COM-N0701", "Upstream 5xx error");
        m.put("COM-N0901", "Gateway error (502/503/504)");

        // SERVER（S）
        m.put("COM-S0001", "Unexpected server error");
        m.put("COM-S0101", "Invalid/Missing configuration");
        m.put("COM-S0301", "Database access error");
        m.put("COM-S0302", "DB unique constraint violation");
        m.put("COM-S0401", "Cache access error");
        m.put("COM-S0501", "Message publish failure");
        m.put("COM-S1201", "Resource exhaustion (threads/fd/memory)");
        m.put("COM-S1301", "Crypto/KMS error");
        m.put("COM-S1601", "Concurrency/lock error");

        // CLIENT（C）
        m.put("COM-C0001", "Malformed request body / serialization error");
        m.put("COM-C0101", "Missing/invalid parameter");
        m.put("COM-C0201", "Validation failed (422)");
        m.put("COM-C0301", "Resource not found (404)");
        m.put("COM-C2001", "Unauthorized (401)");
        m.put("COM-C2101", "Forbidden (403)");
        m.put("COM-C0601", "Payload too large (413)");

        // BUSINESS（B）
        m.put("COM-B0101", "Version/ETag conflict");
        m.put("COM-B0201", "Illegal state transition");
        m.put("COM-B0301", "Quota exceeded");
        m.put("COM-B0601", "Idempotency conflict");

        // UNKNOWN（U）
        m.put("COM-U0000", "Unclassified error (temporary placeholder)");

        LOOKUP = Collections.unmodifiableMap(m);
    }

    /**
     * 常量构造（需要 ErrorCode 类型时复用此方法）
     */
    public static ErrorCode code(String literal) {
        return ErrorCode.of(literal);
    }

    /**
     * 是否为 COM 的保留码
     */
    public static boolean isComCode(String literal) {
        return literal != null && literal.startsWith("COM-") && LOOKUP.containsKey(literal);
    }

    /**
     * 说明查询（未命中返回 null）
     */
    public static String descriptionOf(String literal) {
        return LOOKUP.get(literal);
    }

    /**
     * 提供不可变查表（可用于生成文档）
     */
    public static Map<String, String> allReserved() {
        return LOOKUP;
    }

    // 快捷常量（示例，避免到处写字符串）
    public static final ErrorCode CONNECT_TIMEOUT = code("COM-N0001");
    public static final ErrorCode READ_TIMEOUT = code("COM-N0002");
    public static final ErrorCode CIRCUIT_OPEN = code("COM-N0401");
    public static final ErrorCode RATE_LIMITED = code("COM-N0601");
    public static final ErrorCode UNEXPECTED_SERVER_ERROR = code("COM-S0001");
    public static final ErrorCode VALIDATION_FAILED = code("COM-C0201");
    public static final ErrorCode NOT_FOUND = code("COM-C0301");
    public static final ErrorCode CONFLICT_ETAG = code("COM-B0101");
    public static final ErrorCode UNCLASSIFIED = code("COM-U0000");

    /**
     * 根据类别给出一个“兜底占位码”（避免为 null）
     */
    public static ErrorCode fallbackFor(Category category) {
        return switch (category) {
            case CLIENT -> VALIDATION_FAILED;
            case BUSINESS -> CONFLICT_ETAG;
            case SERVER -> UNEXPECTED_SERVER_ERROR;
            case NETWORK -> CONNECT_TIMEOUT;
            case UNKNOWN -> UNCLASSIFIED;
        };
    }
}
