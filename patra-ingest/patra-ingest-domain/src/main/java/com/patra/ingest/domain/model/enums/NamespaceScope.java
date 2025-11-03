package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 命名空间范围 (字典: ing_namespace_scope)。
 *
 * <p>区分游标命名空间: GLOBAL/EXPR/CUSTOM; 映射到 {@code namespace_scope_code}。
 *
 * <p>范围语义:
 *
 * <ul>
 *   <li>GLOBAL → 全局命名空间,所有数据源共享
 *   <li>EXPR → 表达式哈希命名空间,基于表达式签名隔离
 *   <li>CUSTOM → 自定义命名空间,用户自定义隔离范围
 * </ul>
 */
@Getter
public enum NamespaceScope {
  /** 全局;所有数据源共享的命名空间。 */
  GLOBAL("GLOBAL", "Global namespace"),
  /** 表达式;基于表达式哈希的命名空间。 */
  EXPR("EXPR", "Expression-hash namespace"),
  /** 自定义;用户自定义的命名空间。 */
  CUSTOM("CUSTOM", "Custom namespace");

  private final String code;
  private final String description;

  NamespaceScope(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static NamespaceScope fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("命名空间范围代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (NamespaceScope e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的命名空间范围代码: " + value);
  }
}
