Observability Plan

Tracing
- SkyWalking instrumentation with clear span names: `ingest.plan`, `ingest.execute`, `ingest.relay`, `egress.call`
- Propagate `traceId` and `spanId` through logs; include service and operation tags

Logging
- SLF4J parameterized format only; never concatenate or log secrets
- Mandatory context fields (where applicable): `traceId`, `planId`, `sliceId`, `taskId`, `batchId`, `provenanceCode`, `operationType`
- Mask sensitive headers/values (e.g., `Authorization`, `Cookie`) via a sanitizer in adapters
- Example: `log.info("Executing task {} slice {} plan {}", taskId, sliceId, planId)`

Metrics (Micrometer)
- Timers: `ingest.plan.duration`, `ingest.execute.duration`, `ingest.relay.duration`, `egress.call.duration`
- Counters: `ingest.outbox.published`, `ingest.task.retries`, `egress.call.retry.count`, `errors.by_code`
- Gauges: `mq.consumer.lag`, `db.pool.active`, `ingest.queue.depth`
- Common tags: `service`, `operation`, `provenance`, `result`

Dashboards
- MQ lag trends, DB latency, HTTP success/error rates, retry/circuit metrics, outbox publish rates
- Trace heatmaps for plan/execute/relay flows

Alerts (initial thresholds)
- MQ consumer lag > 2x ingestion rate for 5m
- Error rate > 2% over 5m on critical paths
- DB connection pool saturation > 90% for 5m

Runbooks
- See `operations/Ingest-Runbook.md` for failure handling and recovery steps

Outbox Relay Metrics
- Counters
  - `papertrace.outbox.publish.total{status,channel}` — total publish attempts by status (success, retry, failure)
  - `ingest.task.retries{reason}` — task-level retries; correlate with outbox retries
- Timers
  - `papertrace.outbox.publish.duration{channel,opType,result}` — time to publish a batch or single message
  - `ingest.relay.duration{channel}` — relay orchestration duration per channel
- Gauges/Summaries
  - `mq.consumer.lag{topic}` — broker consumer lag for outbox destinations
  - `ingest.relay.batch.size{channel}` — published batch sizes
- Correlation with Events
  - Use `papertrace.outbox.publish.total{status=success}` as ground truth for published counts
  - Include `outboxMessageId`, `dedupKey`, and `traceId` in logs to bind metrics and traces
