# patra-expr-kernel

## Purpose and Scope
- Pure Java expression kernel for boolean text-matching trees. Provides AST, factories, canonicalization, and a Jackson codec. Designed to be framework-agnostic and portable.

## Core Model
- `Expr` (sealed interface) with nodes: `And`, `Or`, `Not`, `Const`, `Atom`.
- `ExprVisitor<R>`: visitor pattern for traversals and transformations.
- `TextMatch`, `CaseSensitivity`: matching modes and case options.
- Factories: `Exprs` static helpers for concise construction and JSON helpers (`toJson`, `fromJson`).

## Canonicalization
- `ExprCanonicalizer.canonicalize(expr) → ExprCanonicalSnapshot`
  - Produces deterministic JSON (sorted keys, trimmed/collapsed strings, deduped/sorted arrays, normalized numbers) and SHA-256 hash.
  - Use for caching, deduplication, and audit.

## JSON Codec
- `ExprJsonCodec.module()` and `ExprJsonCodec.mapper()` provide serialization without annotating model classes.
- Tolerates unknown properties; suitable for forward-compat fields.

## Examples
- Build an expression
```java
Expr expr = Exprs.and(List.of(
    Exprs.term("title", "heart failure", TextMatch.ANY),
    Exprs.not(Exprs.term("language", "en", TextMatch.EXACT, true)),
    Exprs.rangeDate("pubDate", LocalDate.parse("2023-01-01"), LocalDate.parse("2023-12-31"))
));
```

- Canonicalize and hash
```java
var snap = ExprCanonicalizer.canonicalize(expr);
String canonicalJson = snap.canonicalJson();
String hashHex = snap.hashHex();
```

- JSON round-trip
```java
String json = Exprs.toJson(expr);
Expr parsed = Exprs.fromJson(json);
```

## Boundaries
- No framework dependencies; used by ingest planning and expression-related starters/adapters.
- Rendering/escaping for specific backends (e.g., PubMed, ES) occurs outside the kernel.

## Testing
- See tests under `src/test` for canonicalization (order/dedupe/number trimming) and JSON codec behavior.
