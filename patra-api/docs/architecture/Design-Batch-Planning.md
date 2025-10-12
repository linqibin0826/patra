# Design: Batch Planning (PubMed Focus)

## Summary
- Batch planners translate an `ExecutionContext` into a `BatchPlan` (list of batches and limits) for execution.
- PubMed planner uses offset-based paging (`retstart`/`retmax`) and optionally time filtering parameters.

## Responsibilities
- Respect planner/executor separation: planners decide batch boundaries; executors call providers and upload results.
- Always return stable, deterministic batch lists given the same `ExecutionContext`.

## Inputs
- `ExecutionContext`
  - `compiledQuery` (String): query/term for PubMed.
  - `compiledParams` (Json): may include `retmax`, sort, and others derived by the expression compiler.
  - `windowSpec` (WindowSpec): time/range/volume hints for shaping the plan.
  - `configSnapshot.pagination`: `pageSizeValue`, `maxPagesPerExecution`.

## Outputs
- `BatchPlan`: `batches`, `totalBatches`, `exceedsLimit`.
- `Batch`: `batchNo`, `query`, `params`, `cursorToken=null` (offset paging), `expectedCount` optional.

## PubMed Strategy (Next Increment)
- Page size: prefer `compiledParams.retmax`; else `configSnapshot.pagination.pageSizeValue`; else 100.
- Max pages per execution: `configSnapshot.pagination.maxPagesPerExecution`; default 1.
- Volume budget (records): `pagesNeeded = ceil(limit / retmax)`; `exceedsLimit = pagesNeeded > maxPages`.
- Time window: if missing from params and `WindowSpec.Time` exists, inject `datetype/mindate/maxdate` as per provenance mapping.
- Construct batches: for i in [0..pagesPlanned-1], set `retstart = i*retmax` and `retmax` fixed; reuse query and other params.

## Guardrails
- Idempotency: planning must be pure; do not read remote data.
- Limits: never create more pages than `maxPagesPerExecution`.
- Observability: log `taskId`, `runId`, `provenanceCode`, `pageSize`, and `plannedPages`.

## Open Questions
- Param precedence: should config override compiled params for retmax? (Default proposal: compiled params > config).
- Datetype default: if missing and windowed, which datetype to prefer (`pdat` vs `edat`)? Tie to Registry configuration.

## References
- Contract: `patra-ingest/.../execute/BatchPlanner.java:1`
- Stub: `patra-ingest/.../execute/PubmedBatchPlanner.java:1`
- Execution flow: `ExecuteTaskBatchesUseCaseImpl`
