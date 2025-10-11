Purpose and Responsibilities
- Spring Boot auto-configuration and APIs for compiling/normalizing/rendering provenance expressions.

Key Components
- ExprCompiler and DefaultExprCompiler; normalizer and renderer implementations.
- CapabilityChecker and snapshot loaders for registry-backed rule snapshots.
- Boot config: ExprCompilerAutoConfiguration and CompilerProperties.

Configuration Properties
- `patra.expr.compiler.enabled` (default true)
- `patra.expr.compiler.registry-api.enabled` (default true)
- `patra.expr.compiler.registry-api.operation-default` (default SEARCH)
