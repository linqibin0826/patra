# 12 — Golden Test Harness

Status: Documentation only (pre‑implementation)
Date: 2025-10-15


## Goal

Prevent configuration drift and regressions by asserting that a given (snapshot, expression) pair compiles to an expected (query, params) output with stable warnings/errors.


## Artifacts

Per provider, maintain golden fixtures:
- Snapshot JSON (flattened DTOs from registry or serialized `ProvenanceSnapshot`).
- Expression JSON (canonicalized expr JSON inputs).
- Expected output JSON (`{ query: "...", params: { ... }, warnings: [...], errors: [...] }`).

Directory suggestion:
```
patra-spring-boot-starter-expr/
  src/test/resources/golden/
    pubmed/
      snapshot.json
      expr-phrase-date.json
      expected-phrase-date.json
      expr-or-not.json
      expected-or-not.json
    epmc/
      snapshot.json
      expr-date-query.json
      expected-date-query.json
    crossref/
      snapshot.json
      expr-filter.json
      expected-filter.json
```


## Harness Logic

Pseudocode:
```
for each provider in goldenSets:
  snapshot = load(snapshot.json)
  for each (expr.json, expected.json):
    result = compileWithInjectedSnapshot(expr.json, snapshot)
    assert normalize(result.query)   == normalize(expected.query)
    assert result.params             == expected.params
    assert result.report.warnings    == expected.warnings
    assert result.report.errors      == expected.errors
```
Notes:
- `normalize(query)` may relax whitespace differences while keeping semantic equivalence (e.g., squeeze spaces).
- Keep expected outputs strict for params and issue codes.


## Updating Goldens

When intentional changes modify outputs (e.g., new templates or transforms):
1) Run tests to see diffs.
2) Manually verify diffs align with the design.
3) Update expected JSONs with a clear VCS message referencing the change rationale.

Avoid automatic “accept all” updates to keep signal high.


## CI Integration

- Run golden tests in CI for PRs touching registry seeds, renderer, or compiler.
- Provide a review bot summary highlighting changed cases and diffs (query, params, issues).


## Non‑Goals

- The harness does not hit real provider endpoints (no network). It validates compile‑time outputs only.


## Extensions (Optional)

- Add a “coverage” report: which std_keys/rules are exercised by the golden set per provider.
- Add parameterized runs for MULTI std_key joins and OR/NOT depth to stress parentheses generation.
