package com.patra.expr.canonical;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * Immutable snapshot capturing an expression with its canonical JSON and hash.
 *
 * <p>Used for caching, deduplication, and audit trails where deterministic representation is
 * required. The canonical JSON is normalized with sorted keys and deduplicated arrays.
 *
 * @param expr the original expression
 * @param canonicalJson deterministic JSON representation
 * @param hash SHA-256 hash of the canonical JSON
 */
public record ExprCanonicalSnapshot(Expr expr, String canonicalJson, String hash) {
  public ExprCanonicalSnapshot {
    Objects.requireNonNull(expr, "expr must not be null");
    canonicalJson = canonicalJson == null ? "{}" : canonicalJson;
    Objects.requireNonNull(hash, "hash must not be null");
  }
}
