# Dashboards

## Core Panels (Recommended)
- Outbox Relay
  - Publish rate by channel (counter rate of `papertrace.outbox.publish.total{status=success}`)
  - Relay duration p95 (timer `ingest.relay.duration`)
  - Consumer lag (gauge `mq.consumer.lag{topic}`)
- Ingest Planning/Execution
  - Plan duration p95/p99 (`ingest.plan.duration`)
  - Execute duration p95/p99 (`ingest.execute.duration`)
  - Error rate (counter `errors.by_code`)
- Egress
  - Call duration p95 (`egress.call.duration`)
  - Retry count by reason (`egress.call.retry.count`)

## Trace Heatmaps
- `ingest.plan`, `ingest.execute`, `ingest.relay`, `egress.call` spans

## Alert Hooks
- MQ lag sustained over threshold; error spikes; DB pool saturation
