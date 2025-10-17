# Expression Compiler–Bridge Docs Index

Status: authoritative index for docs/expr
Date: 2025-10-17

- 01 — Overview: ./01-overview.md
- 02 — Architecture: ./02-architecture.md
- 03 — Compiler‑Bridge Internals: ./03-compiler-bridge-internals.md

Providers
- 04 — PubMed: ./04-provider-pubmed.md
- 05 — Europe PMC: ./05-provider-epmc.md
- 06 — Crossref: ./06-provider-crossref.md

Process & Quality
- 07 — Migration Plan: ./07-migration-plan.md
- 08 — Testing: ./08-testing.md
- 09 — Rollout: ./09-rollout.md
- 10 — Risks: ./10-risks.md
- 11 — Acceptance Criteria: ./11-acceptance-criteria.md
- 12 — Golden Test Harness: ./12-golden-test-harness.md
- 12 — Provider Checklist: ./12-provider-checklist.md

How‑To & Smoke
- How to Add a Provider: ./HOW-TO-ADD-PROVIDER.md
- Smoke Tests Guide: ./smoke/SMOKE-TESTS.md
- Registry Verification SQL: ./smoke/registry-verification.sql

Appendices
- 99 — Sample Expressions: ./99-appendix-sample-expressions.md
- 99 — SQL Templates: ./99-appendix-sql-templates.md

Planning Aids (optional)
- Task List: ./task-list.md

Notes
- Metrics naming aligns with code (`ExprMetrics`):
  `expr.render.rule_hits`, `expr.render.rule_miss`, `expr.param.map_hit`,
  `expr.param.map_miss`, `expr.transform.applied`, `expr.compile.errors`,
  `expr.compile.duration_ms`.
