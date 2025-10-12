# ADR 0003: Introduce PubMed BatchPlanner

Status: Accepted

Date: 2025-10-12

Context
- We execute ingestion tasks in batches. The application layer requires a `BatchPlanner` per provenance to translate an `ExecutionContext` (compiled query, params, window, and config snapshot) into a concrete list of batches to execute.
- PubMed uses E-Utilities with offset-based pagination (`retstart`/`retmax`) and optional date filters (`datetype`, `mindate`, `maxdate`).
- `ExecuteTaskBatchesUseCaseImpl` routes planning via `BatchPlannerRegistry` using `provenanceCode` and expects a `BatchPlan` with batch list and limit knowledge.

Decision
- Add a concrete planner `PubmedBatchPlanner` implementing `BatchPlanner` and registering via Spring `@Component`.
- In this first increment, deliver an initial implementation that returns a single batch using the compiled query and parameters. This makes the contract executable and unlocks executor development/testing.
- Defer full pagination/window-aware planning to a subsequent increment governed by this ADR and the design notes below.

Alternatives
- Implement full pagination now: Higher initial complexity, slower feedback. We prefer incremental delivery.
- Have executors perform pagination loops: Violates separation of concerns; planning belongs to planners.

Consequences
- Short term: one-batch plans for PubMed; safe baseline with no external calls.
- Next step: implement offset-based pagination and `WindowSpec` mapping with guardrails (max pages, page size source of truth).

Design Notes (next increment)
- Page size: prefer `compiledParams.retmax` when present; else fallback to `configSnapshot.pagination.pageSizeValue`; else default 100.
- Max pages per execution: use `configSnapshot.pagination.maxPagesPerExecution`; if null, default 1.
- Volume budgets: for `WindowSpec.VolumeBudget(unit=RECORDS)`, `pagesNeeded = ceil(limit/retmax)`, set `exceedsLimit = pagesNeeded > maxPages`.
- Time windows: if `WindowSpec.Time` present and `datetype/mindate/maxdate` absent in params, derive and inject them (subject to provenance mapping rules from Registry/Expr compiler).
- Batch items: for i in [0..pagesPlanned-1], set `retstart = i*retmax` and reuse `retmax`; keep `cursorToken` null for offset paging.

References
- Code: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/execute/PubmedBatchPlanner.java:1`
- Planner contract: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/execute/BatchPlanner.java:1`
- Execution context: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/ExecutionContext.java:1`
