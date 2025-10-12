# QA Sign-off Checklist

## Functional
- All acceptance criteria met.
- Regression tests pass.

## Non-Functional
- Observability: logs/metrics/traces present and useful.
- Performance: targets met or measured with baseline.
- Resilience: retry/backoff/circuit configured where applicable.

## Documentation
- Service README updated.
- Contracts updated or explicitly unchanged.
- Runbooks updated.

## Risk
- Rollback path understood.
- No P0/P1 defects open.
