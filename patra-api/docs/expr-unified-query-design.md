# Unified Expression Rendering and Cross‑Provider Query Construction (std_key–based)

Status: Refined design set (documentation only)
Date: 2025-10-15
Owners: Ingest/Registry/Expr Starter teams
Scope: patra-registry, patra-expr-kernel, patra-spring-boot-starter-expr, patra-spring-boot-starter-provenance, patra-ingest


## Purpose of This Set

This index anchors the full documentation set for the compiler‑bridge variant that normalizes the boolean query to `std_key=query` and routes it through the registry’s std_key → provider parameter mapping. The detailed design is split into focused documents:

- 01 Overview — `docs/expr/01-overview.md`
- 02 Architecture — `docs/expr/02-architecture.md`
- 03 Compiler‑Bridge Internals — `docs/expr/03-compiler-bridge-internals.md`
- 04 Provider: PubMed — `docs/expr/04-provider-pubmed.md`
- 05 Provider: Europe PMC — `docs/expr/05-provider-epmc.md`
- 06 Provider: Crossref — `docs/expr/06-provider-crossref.md`
- 07 Migration Plan (DDL/Seeds) — `docs/expr/07-migration-plan.md`
- 08 Testing & Validation — `docs/expr/08-testing.md`
- 09 Rollout & Operations — `docs/expr/09-rollout.md`
- 10 Risks & Mitigations — `docs/expr/10-risks.md`
- 11 Acceptance Criteria & Checklists — `docs/expr/11-acceptance-criteria.md`
- 99 Appendix: SQL Templates — `docs/expr/99-appendix-sql-templates.md`
- 99 Appendix: Sample Expressions — `docs/expr/99-appendix-sample-expressions.md`


## Executive Summary

We will:
- Promote the boolean query to a first-class std_key `query` and bridge it to provider parameters in the compiler (not in the renderer or adapter code).
- Enable OR/NOT rendering and rule-driven negation so boolean logic works across providers.
- Execute `fn_code` and `transform_code` to handle provider-specific quirks without code branching.
- Normalize PARAMS placeholders to the engine’s `{{...}}` convention.
- Provide seeds for PubMed, Europe PMC, Crossref so the system can adapt by configuration.

See each section document for implementation-ready details. No code changes will be performed until this set is fully reviewed and approved.
