Observability Plan

- Tracing: SkyWalking instrumentation; include correlation/trace IDs in logs.
- Logging: SLF4J with parameterized messages; mask secrets; include planId/sourceId/batchId where applicable.
- Metrics: Micrometer timers for key use cases (planning, execution, outbox relay, egress), error counters by code.
- Dashboards: MQ lag, DB latency, HTTP success rates, retry/circuit metrics.
- Alerts: MQ consumer lag thresholds, error-rate spikes, DB connection pool saturation.

