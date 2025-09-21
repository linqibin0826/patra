package com.patra.ingest.domain.model.vo;

/** 幂等键值对象。 */
public record IdempotentKey(String value) {
    public IdempotentKey {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException("IdempotentKey must be 64-char SHA256 hex");
        }
    }
}
