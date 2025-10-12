# Registry Runbook

## Purpose
- Operate `patra-registry`: serve provenance metadata/config and expression snapshots reliably.

## How to Run Locally
- Start infra:
```bash
cd docker/compose && docker compose up -d
```
- Launch service:
```bash
./mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run
```

## Health and Diagnostics
- Health: `/actuator/health` (DB, config)
- Logs: include `provenanceCode`, `operationType`, and query params
- Metrics: query latency timers, snapshot build time, cache hit ratio (if enabled)

## Common Failures
- DB latency or connection errors → verify MySQL and indices for read paths
- Config staleness → ensure Nacos connectivity; refresh scope as needed
- Snapshot load errors → check data quality and JSON validation

## Rollback/Recovery
- Redeploy previous version; snapshots are read-only—no rollback needed.
- Rebuild indices; validate Flyway baseline alignment.
