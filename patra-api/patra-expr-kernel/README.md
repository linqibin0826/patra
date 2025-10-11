Purpose and Responsibilities
- Pure Java expression kernel for boolean text-matching trees; provides canonicalization and JSON codec.

Key Types
- Core: Expr, And, Or, Not, Atom, TextMatch, Const; visitors via ExprVisitor.
- Canonical: ExprCanonicalizer and ExprCanonicalSnapshot to normalize structures.
- JSON: ExprJsonCodec encodes/decodes expressions.

Boundaries
- No framework dependencies; used by ingest planning and expression starter.

Testing
- See tests under src/test for canonicalization and JSON codec behavior.
