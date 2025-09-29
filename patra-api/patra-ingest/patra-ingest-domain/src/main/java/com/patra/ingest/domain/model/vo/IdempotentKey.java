package com.patra.ingest.domain.model.vo;

/**
 * 幂等键值对象（固定为 64 字符十六进制 SHA256）。
 * <p>用于计划 / 任务等对象的业务去重。创建时强制校验长度。</p>
 */
public record IdempotentKey(String value) {
    public IdempotentKey {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException("IdempotentKey must be 64-char SHA256 hex");
        }
    }
}
