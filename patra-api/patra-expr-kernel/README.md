# patra-expr-kernel

Sealed expression AST used by Papertrace services. The module provides:

- `Expr` visitor-friendly root interface (permits `And`, `Or`, `Not`, `Const`, `Atom`).
- `Atom` value hierarchy for TERM / IN / RANGE / EXISTS / TOKEN operators.
- `Exprs` static factories for convenience construction.
- `CaseSensitivity` and `TextMatch` enums shared across modules.

All node types are immutable Java records or enums, making the tree inherently thread-safe. Prefer visiting the tree through `Expr.accept(Visitor)` rather than via reflection.

