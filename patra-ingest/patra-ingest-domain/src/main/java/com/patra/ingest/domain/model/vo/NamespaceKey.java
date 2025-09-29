package com.patra.ingest.domain.model.vo;

import com.patra.ingest.domain.model.enums.NamespaceScope;

/**
 * 命名空间组合键。
 * <p>由 Scope（作用域）与具体 key 组成；GLOBAL 作用域约定使用 64 位 '0' 占位。</p>
 */
public record NamespaceKey(NamespaceScope scope, String key) {
    /**
     * 全局命名空间固定键（key = 64 个 0）
     */
    public static NamespaceKey global() {
        return new NamespaceKey(NamespaceScope.GLOBAL, "0".repeat(64));
    }
}
