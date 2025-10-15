# 01 — Overview

Status: Documentation only (pre‑implementation)
Date: 2025-10-15
Owners: Ingest/Registry/Expr Starter teams


## 1.1 Problem Statement

The current expression (expr) pipeline renders:
- a provider‑specific boolean query string (e.g., PubMed `term`) and
- a set of provider‑named parameters (e.g., `mindate`, `maxdate`, `retmax`).

Today, the boolean query is not routed via the registry’s `std_key` mapping. Instead, adapter code binds the query to provider parameters directly. That prevents a unified, configuration‑driven approach to multi‑provider query construction and creates unnecessary code specialization.


## 1.2 Goals

- Treat the boolean query as a first‑class `std_key=query`.
- Route all output—query and params—through the registry’s std_key → provider mapping (temporal, endpoint‑aware).
- Enable cross‑provider boolean semantics by adding OR/NOT rendering and rule‑driven negation.
- Execute rule‑level functions (`fn_code`) and param‑level transforms (`transform_code`) to encode provider quirks declaratively.
- Normalize `PARAMS` rule templates to use engine placeholders `{{...}}`.
- Provide production‑ready seeds for PubMed, Europe PMC, Crossref.


## 1.3 Non‑Goals

- Changing public API contracts in registry; DTOs already include required fields.
- Changing expression JSON or AST in `patra-expr-kernel`; we extend renderer/compiler behavior only.


## 1.4 Approach Summary (Compiler‑Bridge Variant)

- Renderer:
  - Renders atoms into query fragments (`emit=QUERY`) and emits std_key/value pairs for `emit=PARAMS` (using `{{...}}` placeholders).
  - Adds OR/NOT support; uses rule selection’s `negation`/`match`/`valueType` keys; renderer does not map provider parameter names.
  - Executes `fn_code` before final param value substitution at std_key stage (rule scope).

- Compiler:
  - Bridges the aggregated boolean query into the output params by looking up `std_key=query` in the snapshot’s param map and setting the provider param name to the boolean query value.
  - Maps all std_keys to provider names and applies `transform_code` to mapped params (including the bridged query) prior to returning the final output.

This keeps rendering and provider naming concerns separate while making everything configurable via the registry.


## 1.5 Glossary

- std_key: a provider‑neutral semantic key (e.g., `query`, `from`, `to`, `limit`, `offset`, `filter`).
- PARAMS rule: render rule with `emit=PARAMS` that produces std_key/value pairs via a params JSON object.
- QUERY rule: render rule with `emit=QUERY` that produces a boolean query fragment string.
- fn_code: rule‑level function applied during PARAMS rendering (template scope).
- transform_code: param‑level transform applied after std_key → provider mapping (value scope).
- Cardinality: whether a std_key is `SINGLE` (one value) or `MULTI` (many). `MULTI` may be joined via transforms or emitted as repeated provider parameters.


## 1.6 Success Criteria

- All providers receive the final boolean query through std_key mapping (no hardcoded binding in adapters).
- OR/NOT expressions render into correct provider query syntax where configured.
- Provider quirks (e.g., PubMed `datetype`, exclusive `to`) are handled via `fn_code` and `transform_code`.
- Seeds for PubMed/EPMC/Crossref are sufficient to run end‑to‑end without code changes in adapters.
