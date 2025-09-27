package com.patra.expr.canonical;

import com.patra.expr.Expr;

import java.util.Objects;

/**
 * 表达式的规范化快照，封装原始表达式与其确定性 JSON 及散列值。
 */
public record ExprCanonicalSnapshot(Expr expr, String canonicalJson, String hash) {
    public ExprCanonicalSnapshot {
        Objects.requireNonNull(expr, "expr不能为空");
        canonicalJson = canonicalJson == null ? "{}" : canonicalJson;
        Objects.requireNonNull(hash, "hash不能为空");
    }
}
