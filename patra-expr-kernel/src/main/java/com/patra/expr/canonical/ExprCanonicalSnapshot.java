package com.patra.expr.canonical;

import com.patra.expr.Expr;
import java.util.Objects;

/// 不可变快照,捕获表达式及其规范化 JSON 和哈希值。
///
/// 用于需要确定性表示的缓存、去重和审计跟踪场景。规范化 JSON 具有排序的键和去重的数组。
///
/// @param expr 原始表达式
/// @param canonicalJson 确定性的 JSON 表示
/// @param hash 规范化 JSON 的 SHA-256 哈希值
/// @author linqibin
/// @since 0.1.0
public record ExprCanonicalSnapshot(Expr expr, String canonicalJson, String hash) {
  public ExprCanonicalSnapshot {
    Objects.requireNonNull(expr, "expr must not be null");
    canonicalJson = canonicalJson == null ? "{}" : canonicalJson;
    Objects.requireNonNull(hash, "hash must not be null");
  }
}
