package com.patra.ingest.domain.model.vo;

import com.patra.ingest.domain.model.enums.NamespaceScope;

/** 命名空间组合键。 */
public record NamespaceKey(NamespaceScope scope, String key) {
    public static NamespaceKey global() { return new NamespaceKey(NamespaceScope.GLOBAL, "0".repeat(64)); }
}
