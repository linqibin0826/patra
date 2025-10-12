# Test Plan: PubMed BatchPlanner

Owner: Test Engineer (A/R); Dev (C); Architect (I)

Scope
- Validate `PubmedBatchPlanner` compliance with the BatchPlanner contract.
- Ensure planning is deterministic and respects configuration and window strategies.

Non-Functional Goals
- Deterministic output for same input
- No network IO in planning
- Logs include `taskId`, `runId`, `provenanceCode`

Test Matrix

1) Initial behavior (this increment)
- Given any ExecutionContext for PUBMED, planner returns a single Batch with `query=compiledQuery` and `params=compiledParams`.
- `exceedsLimit=false`, `totalBatches=1`.

2) Pagination (next increment)
- retmax precedence: when compiledParams.retmax present -> page size equals it; else fallback to snapshot pagination.pageSizeValue; else default 100.
- max pages: respect snapshot.pagination.maxPagesPerExecution; when null -> default 1.
- produce batches with retstart = i*retmax; no cursorToken.

3) Window mapping (next increment)
- Time: when WindowSpec.Time present and params lack `datetype/mindate/maxdate`, inject them.
- Volume: when VolumeBudget(RECORDS, limit) present, compute pagesNeeded and set `exceedsLimit` if pagesNeeded > maxPages.

4) Error handling
- Null/blank query -> planner should fail fast (domain Batch invariant enforces non-blank query when building batches).
- Negative/zero retmax is ignored in production logic (defensive defaults); not applicable in this initial baseline.

CI Execution
- Unit tests under `patra-ingest-app` for planner planning logic (TBD in next increment).
- Run: `./mvnw -q -pl patra-ingest/patra-ingest-app test`

Traceability
- Requirement: Executable batch planning contract for PubMed (stub) → ADR-0003
- Design: docs/architecture/Design-Batch-Planning.md
