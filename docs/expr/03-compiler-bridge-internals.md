# 03 — Compiler‑Bridge Internals

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## 3.1 Overview

The compiler‑bridge is the post‑render step that:
1) injects the aggregated boolean query into the outgoing provider params via `std_key=query` mapping, and
2) applies `transform_code` to every mapped value.

Renderer keeps rendering concerns only (atoms → fragments/std_keys). Naming of provider parameters and value transforms are compiler concerns.


## 3.2 Algorithm (pseudocode)

```
compile(request):
  snapshot = snapshotLoader.load(request.provenance, request.operationType, request.endpointName)
  normalized = normalizer.normalize(request.expression, request.options.strict)
  issues = capabilityChecker.check(normalized, snapshot, request.options.strict)
  if issues.hasErrors(): return CompileResult(emptyQuery, emptyParams, normalized, report(issues), ref(snapshot), traceIfEnabled)

  outcome = renderer.render(normalized, snapshot, request.options.traceEnabled)

  // Start with renderer-produced std_key params mapped to provider names
  // Note: renderer only produces std_keys; mapping happens here.
  mapped = new LinkedHashMap<String,String>()
  for each (stdKey, valueTemplateResolved) in outcome.stdKeyParams:
      mapping = snapshot.apiParameterMap.get(stdKey)
      if mapping is null:
         warn "W-PARAM-MAP-MISSING", stdKey; continue
      value = valueTemplateResolved
      if mapping.transformCode != null:
         value = transformRegistry.apply(mapping.transformCode, stdKey, value, snapshot)
      mapped.put(mapping.providerParamName, value)

  // Bridge the aggregated boolean query via std_key=query (if configured)
  if outcome.query not blank:
      mapping = snapshot.apiParameterMap.get("query")
      if mapping != null and !mapped.containsKey(mapping.providerParamName):
         value = outcome.query
         if mapping.transformCode != null:
            value = transformRegistry.apply(mapping.transformCode, "query", value, snapshot)
         mapped.put(mapping.providerParamName, value)

  // Enforce query length budget if provided
  if request.options.maxQueryLength > 0 and length(outcome.query) > request.options.maxQueryLength:
      addError("E-QUERY-LEN-MAX", max=request.options.maxQueryLength, actual=length(outcome.query))

  return CompileResult(outcome.query, mapped, normalized, report(mergedWarnings, mergedErrors), ref(snapshot), outcome.trace)
```

Notes:
- Renderer’s PARAMS output should be a map of std_key → renderedValue; provider naming is resolved in this step.
- The compiler must merge renderer warnings with any validation warnings.


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


## 3.4 Error Handling & Reporting

- Missing param map for a std_key → warning `W-PARAM-MAP-MISSING` (result param omitted).
- Missing render rule for an atom → warning `W-RENDER-RULE-MISSING` (fragment skip).
- Query length overflow → error `E-QUERY-LEN-MAX` (empty result returned as error state).
- Transform/function code not found → warning `W-FN-OR-TRANSFORM-NOTFOUND` and proceed without applying.


## 3.5 Logging & Metrics

- INFO: `compiled expr for provenance={code}, endpoint={name}, queryLen={n}, params={size}`
- DEBUG: per std_key mapping `{stdKey -> providerParamName}`, transform applied `{transformCode}`, and bridge `{query -> providerParamName}`
- WARN counters: rule misses, param map misses, transform/function not found


## 3.6 Configuration

- `patra.expr.compiler.query-param-bridge.enabled` (bool, default true): toggle bridging behavior.
- `patra.expr.compiler.max-query-length` (int, default 0=disabled): optional global fallback if caller doesn’t set.


## 3.7 Thread Safety & Performance

- Registries are immutable maps after boot; lookups are O(1).
- Rendering and transforms are per‑compile and free of shared mutable state.
- Keep allocations low; use `StringBuilder` for fragment joins.
