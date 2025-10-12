# NFR Matrix

## Availability
- Target: 99.9% for core ingest/registry paths
- How Measured: uptime probes (HTTP), MQ consumer lag windows, error budgets (0.1% monthly)
- Notes: use readiness probes to gate traffic during migrations

## Latency
- Target: Plan creation p95 < 2s per plan; egress round-trip p95 < 500ms (internal network)
- How Measured: trace spans (plan, execute, relay, egress), adapter HTTP timers
- Notes: execution latency bounded by batch size and external APIs

## Throughput
- Target: ≥ 100 msgs/s outbox relay sustained; spikes to 500 msgs/s
- How Measured: RocketMQ publish rate, consumer lag, relay batch metrics
- Notes: tune batch sizes and DB indices; monitor backpressure

## Security/Privacy
- Target: zero secrets in logs; least privilege for DB/MQ; provenance-specific PII rules
- How Measured: log scanners, config audits, periodic reviews
- Notes: mask sensitive headers; store secrets in Nacos/env only

## Cost
- Target: cost-aware MQ throughput and storage; optimized DB indices
- How Measured: infra dashboards (broker I/O, DB CPU/IO), storage growth
- Notes: prefer incremental scaling; archive old outbox records

## Reliability/Resilience
- Target: idempotent processing; bounded retries with backoff; circuit breakers for egress
- How Measured: retry counts, DLQ volume (if any), error-rate spikes
- Notes: outbox guarantees; partitioned ordering by business key

## Measurement Tooling
- SLIs: HTTP latency, MQ lag, DB latency, error rate, retry count
- Tools: SkyWalking traces, Micrometer metrics, logs with correlation IDs, broker dashboards
