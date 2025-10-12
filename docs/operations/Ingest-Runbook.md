# Ingest Runbook

## Purpose
- Operate `patra-ingest` safely: plan scheduling, task execution, and outbox relay.

## How to Run Locally
- Start infra:
```bash
cd docker/compose && docker compose up -d
```
- Launch service:
```bash
./mvnw -pl patra-ingest/patra-ingest-boot -am spring-boot:run
```

## Health and Diagnostics
- Health: `/actuator/health` (include DB, MQ, Redis if enabled)
- Logs: look for planId, sliceId, taskId, batchId with trace/correlation IDs
- Traces: SkyWalking spans for planning, execution, relay
- Metrics: timers for Plan, Execute, Relay; consumer lag on RocketMQ topics

## Common Failures
- DB connectivity issues → verify MySQL and Flyway migrations
- MQ publish failures → check RocketMQ broker/connectivity; relay retries/backoff
- Lease/lock contention → validate Redis availability and time sync
- Expression compile errors → verify registry snapshot and provenance configuration

## Rollback/Recovery
- Re-run plan: idempotent by planKey; failed tasks are retried
- Outbox replay: enable relay and re-publish by channel/partition filters
- Restore cursor: adjust cursor events or use admin tool to reset window for a provenance

## Outbox Relay Troubleshooting
- Symptoms: low publish rate, growing MQ lag, rising `papertrace.outbox.publish.total{status=retry|failure}`
- Checks
  - Broker health and topic availability; partition assignments
  - DB hot spots (outbox table scans); validate indices on state/channel/lease fields
  - Batch size and lease configuration; reduce batch if timeouts occur
- Correlate
  - TaskReady v1 publish rate vs `papertrace.outbox.publish.total{status=success}`
  - Trace `papertrace.outbox.publish.duration` spikes with broker/network incidents
