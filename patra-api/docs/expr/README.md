# Expression Compiler–Bridge — Index

Status: authoritative index for docs/expr (updated 2025-10-17)

Start Here
- START-HERE: ./START-HERE.md

Core
- 01 — Overview: ./01-overview.md
- 02 — Architecture: ./02-architecture.md
- 03 — Compiler‑Bridge Internals: ./03-compiler-bridge-internals.md

How-To
- 12 — Provider Onboarding: ./12-provider-onboarding.md
- 04 — Providers Quick Reference: ./04-providers-quickref.md

Quality & Delivery
- 08 — Testing and Smoke: ./08-testing-and-smoke.md
- 07 — Migration and Rollout: ./07-migration-rollout.md
- 11 — Acceptance Criteria: ./11-acceptance-criteria.md

Appendices
- 99 — Sample Expressions: ./99-appendix-sample-expressions.md
- 99 — SQL Templates: ./99-appendix-sql-templates.md
- Registry Verification SQL: ./smoke/registry-verification.sql

Archive (moved on 2025-10-17)
- Provider Deep Dives: ./archive/04-provider-pubmed.md, ./archive/05-provider-epmc.md, ./archive/06-provider-crossref.md
- Reviews & Checklists: ./archive/design-review.md, ./archive/peer-review.md, ./archive/final-review.md, ./archive/pr-checklist.md, ./archive/task-list.md
- Risks: ./archive/10-risks.md
- Consolidated Originals: ./archive/07-migration-plan.md, ./archive/09-rollout.md, ./archive/08-testing.md, ./archive/12-golden-test-harness.md, ./archive/12-provider-checklist.md, ./archive/HOW-TO-ADD-PROVIDER.md, ./archive/SMOKE-TESTS.md

Notes
- Metrics naming aligns with code (`ExprMetrics`):
  `expr.render.rule_hits`, `expr.render.rule_miss`, `expr.param.map_hit`,
  `expr.param.map_miss`, `expr.transform.applied`, `expr.compile.errors`,
  `expr.compile.duration_ms`.
