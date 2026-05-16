package dev.linqibin.patra.ingest.domain.model.vo.shared;

import dev.linqibin.patra.ingest.domain.model.enums.NamespaceScope;

/// 命名空间键值对象。
///
/// 由作用域和值组成的复合键,用于多租户隔离。
///
/// GLOBAL 作用域约定使用 64 个 `'0'` 字符作为占位符。
///
/// @param scope 命名空间作用域
/// @param key 命名空间键值
/// @author linqibin
/// @since 0.1.0
public record NamespaceKey(NamespaceScope scope, String key) {
  /// 创建全局命名空间键的工厂方法 (64 个零)。
  public static NamespaceKey global() {
    return new NamespaceKey(NamespaceScope.GLOBAL, "0".repeat(64));
  }
}
