package com.patra.expr.canonical;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * Immutable snapshot that captures the original expression together with its canonical JSON payload
 * and hash digest.
 */
public record ExprCanonicalSnapshot(Expr expr, String canonicalJson, String hash) {
  public ExprCanonicalSnapshot {
    Objects.requireNonNull(expr, "expr must not be null");
    canonicalJson = canonicalJson == null ? "{}" : canonicalJson;
    Objects.requireNonNull(hash, "hash must not be null");
  }
}
