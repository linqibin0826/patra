# 03 — Compiler‑Bridge Internals

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 3.1 Overview

The compiler‑bridge is the post‑render step that:
1) injects the aggregated boolean query into the outgoing provider params via `std_key=query` mapping, and
2) applies `transform_code` to every mapped value.

Renderer keeps rendering concerns only (atoms → fragments/std_keys). Provider naming and value transforms are compiler responsibilities (single naming stage).


## 3.2 Algorithm (pseudocode)

```
compile(request):
  snapshot = snapshotLoader.load(request.provenance, request.operationType, request.endpointName)
  normalized = normalizer.normalize(request.expression, request.options.strict)
  issues = capabilityChecker.check(normalized, snapshot, request.options.strict)
  if issues.hasErrors(): return CompileResult(emptyQuery, emptyParams, normalized, report(issues), ref(snapshot), traceIfEnabled)

  outcome = renderer.render(normalized, snapshot, request.options.traceEnabled)
  // outcome.stdKeyParams : Map<std_key,String> (renderer does NOT map provider names)
  // outcome.query        : String aggregated from fragments (may be blank)

  // 1) Map std_keys from renderer to provider parameter names
  mapped = new LinkedHashMap<String,String>()  // providerParamName -> value (SINGLE policy by default)
  for each (stdKey, stdValue) in outcome.stdKeyParams:
      mapping = snapshot.apiParameterMap.get(stdKey)
      if mapping is null:
         warn "W-PARAM-MAP-MISSING", stdKey; continue
      value = stdValue
      if mapping.transformCode != null:
         value = transformRegistry.apply(mapping.transformCode, stdKey, value, snapshot)
      mapped.put(mapping.providerParamName, value)

  // 2) Bridge the aggregated boolean query via std_key=query (if configured)
  if outcome.query not blank:
      mapping = snapshot.apiParameterMap.get("query")
      if mapping != null and !mapped.containsKey(mapping.providerParamName):
         value = outcome.query
         if mapping.transformCode != null:
            value = transformRegistry.apply(mapping.transformCode, "query", value, snapshot)
         mapped.put(mapping.providerParamName, value)
      else if mapping != null:
         warn "W-QUERY-BRIDGE-DUP", mapping.providerParamName

  // 3) Enforce query length budget if provided
  if request.options.maxQueryLength > 0 and length(outcome.query) > request.options.maxQueryLength:
      addError("E-QUERY-LEN-MAX", max=request.options.maxQueryLength, actual=length(outcome.query))

  return CompileResult(outcome.query, mapped, normalized, report(mergedWarnings, mergedErrors), ref(snapshot), outcome.trace)
```

Notes:
- Renderer’s PARAMS output is a map of std_key → renderedValue; provider naming is resolved only here (in the compiler).
- The compiler must merge renderer warnings with any validation warnings.


## 3.2.1 Execution Order Contract (formal)

```
[Atom]
  -> placeholders (field/op/value → {{v}}, {{from}}, {{to}}, {{quoted}}, …)
  -> (renderer) apply fn_code (rule-level) to derive/adjust placeholder values
  -> (renderer) expand PARAMS templates → std_key → value
  -> (renderer) produce QUERY fragments and std_key/value(s); NO provider naming
  -> (compiler) aggregate fragments → aggregated boolean query
  -> (compiler) bridge std_key=query via param map (if mapping exists)
  -> (compiler) map all std_keys → providerParamName
  -> (compiler) apply transform_code (param-level) on each mapped value
  -> (compiler) return provider-named params + aggregated query
```

Implications:
- Functions operate in std_key/placeholder space (provider-agnostic).
- Transforms operate on final mapped values (provider-specific semantics).


## 3.3 Function and Transform Registries

### 3.3.1 Interfaces

```java
public interface RenderFunction {
  String code();
  // Mutates/produces placeholder values for PARAMS rendering
  String apply(Map<String, String> placeholders, ProvenanceSnapshot snapshot);
}

public interface ValueTransform {
  String code();
  // Applies to a single mapped std_key value before returning final provider param value
  String apply(String stdKey, String value, ProvenanceSnapshot snapshot);
}

public interface FunctionRegistry {
  Optional<RenderFunction> find(String code);
}

public interface TransformRegistry {
  Optional<ValueTransform> find(String code);
}
```

### 3.3.2 Built‑ins (initial)

- `PUBMED_DATETYPE` (fn): returns `"pdat"` initially. Later: use snapshot or rule context to select `pdat/edat`.
- `TO_EXCLUSIVE_MINUS_1D` (transform): subtract one day from `to` (date granularity) to convert exclusive end into inclusive provider bound.
- Optional date normalizers: `RFC3339_DATE`, `RFC3339_DATETIME`.

## 3.3.3 Value Escaping and Encoding

- Renderer handles quoting/escaping inside boolean query fragments (e.g., `"{{v}}"`).
- For PARAMS values, avoid adding provider-specific quoting in templates; rely on transforms for formatting, and on the HTTP client for URL encoding.


## 3.4 Error Handling & Reporting

### 3.4.1 Error/Warning Codes

| Code | Severity | Description | STRICT Mode Behavior | Operator Action |
|------|----------|-------------|----------------------|-----------------|
| `E-QUERY-LEN-MAX` | ERROR | Query exceeds maxQueryLength | Always error | Refactor expression or increase limit |
| `E-NOT-UNSUPPORTED` | ERROR | Provider doesn't support NOT | Error if strict=true | Use provider-specific negation template |
| `E-FN-NOTFOUND` | ERROR | Function code not found | Error if strict=true | Fix seed configuration |
| `E-TRANSFORM-NOTFOUND` | ERROR | Transform code not found | Error if strict=true | Fix seed configuration |
| `E-PARAM-COUNT-LIMIT` | ERROR | Parameters exceed hard limit | Always error (if configured) | Reduce params via MULTI+join |
| `W-PARAM-MAP-MISSING` | WARNING | No mapping for std_key | Warning always | Add param map in seeds |
| `W-RENDER-RULE-MISSING` | WARNING | No render rule for atom | Warning always | Add render rule in seeds |
| `W-NOT-SKIPPED` | WARNING | NOT skipped (unsupported) | Warn if strict=false | Review query semantics |
| `W-FN-OR-TRANSFORM-NOTFOUND` | WARNING | Function/transform not found | Warn if strict=false | Fix seed configuration |
| `W-PARAM-COUNT-LIMIT` | WARNING | Parameters exceed soft limit | Warning always | Consider MULTI+join strategy |
| `W-QUERY-BRIDGE-DUP` | WARNING | Query bridging skipped because provider param already populated | Warning always | Remove duplicate provider-side mapping if bridging should win |
| `W-MULTI-REPEAT-NOTSUPPORTED` | WARNING | MULTI repeat requested but not yet supported; compiler falls back to join encoding | Warning always | Provide join transform or delay enabling repeat until adapters support it |

### 3.4.2 STRICT Mode Behavior

When `expr.strict=true`:
- Compilation fails fast on missing functions/transforms or unsupported capabilities
- Ensures deterministic behavior across environments
- Recommended for production

When `expr.strict=false` (default):
- Gracefully degrade with warnings
- Continue compilation with best effort
- Useful during development/migration


## 3.5 Logging & Metrics

- INFO: `compiled expr for provenance={code}, endpoint={name}, queryLen={n}, params={size}`
- INFO redaction: log `queryHash` or last 8 chars; do not log full query content in prod at INFO.
- DEBUG (non‑prod): per std_key mapping `{stdKey -> providerParamName}`, transform applied `{transformCode}`, and bridge `{query -> providerParamName}`
- WARN counters: rule misses, param map misses, transform/function not found


## 3.6 Configuration

- `patra.expr.compiler.query-param-bridge.enabled` (bool, default true): toggle bridging behavior.
- `patra.expr.compiler.max-query-length` (int, default 0=disabled): optional global fallback if caller doesn't set.
- `expr.strict` (bool, default false): enable STRICT mode for fail-fast behavior on missing capabilities.
- `expr.multi.repeat.enabled` (bool, default false): allow MULTI std_keys to emit repeated parameters (requires adapter support).
- `patra.expr.compiler.warn-param-count` (int, optional): soft limit for parameter count warnings (W-PARAM-COUNT-LIMIT).
- `patra.expr.compiler.max-param-count` (int, optional): hard limit for parameter count errors (E-PARAM-COUNT-LIMIT).


## 3.7 Thread Safety & Performance

- Registries are immutable maps after boot; lookups are O(1).
- Rendering and transforms are per‑compile and free of shared mutable state.
- Keep allocations low; use `StringBuilder` for fragment joins.


## 3.8 Merge Policy Details (SINGLE vs MULTI)

- SINGLE: std_key accepts one value. When multiple emissions occur:
  - Prefer the value emitted by the highest‑priority rule. For rules with equal priority, apply deterministic ordering: `rule_priority DESC, field_key ASC, op_code ASC, rule_id ASC`.
  - Example: two date ranges for the same field → last‑write‑wins by this ordering; earlier ones are superseded.
- MULTI: std_key collects many values.
  - Join strategy (default): a transform (e.g., `LIST_JOIN(';')` or `FILTER_JOIN`) converts the list into one string before mapping or after mapping (depending on transform design).
  - Repeat strategy (gated): compiler maintains a Map<String,List<String>> internally and a provider encoder repeats parameters. Requires `expr.multi.repeat.enabled=true` configuration. Until provider adapters implement repeat encoding, the compiler logs `W-MULTI-REPEAT-NOTSUPPORTED` and falls back to join-style encoding of the `||`-delimited value.
  - Recommendation: use Join strategy for Crossref `filter` and EPMC multi-term cases; Repeat strategy disabled by default until adapter serialization is documented.

## 3.9 Limits & Bounds

- Max Query Length:
  - Enforced via request option or `patra.expr.compiler.max-query-length` fallback.
  - On overflow: error `E-QUERY-LEN-MAX`; compiler returns empty query/params with report populated.
  - No automatic trimming is performed to avoid changing semantics; prefer transforms or expression refactors.
- Max Parameter Count:
  - Not enforced by default (provider limits vary).
  - Recommendation: introduce a soft warning threshold (e.g., `patra.expr.compiler.warn-param-count=N`) that logs a warning `W-PARAM-COUNT-LIMIT` when exceeded; adjust seeds or transforms to reduce parameter explosion (prefer MULTI+join).
  - If a hard limit is needed for a provider, add an environment‑specific guard and fail with `E-PARAM-COUNT-LIMIT`.
