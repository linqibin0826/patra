NFR Matrix

- Availability: Target 99.9% (core ingest path); measured via uptime probes and MQ lag SLOs.
- Latency: Ingest planning < 2s p95 per plan; task execution bounded by batch size; measured via traces.
- Throughput: Sustain 100+ msgs/s on outbox relay; measured via consumer lag and publish rates.
- Security/Privacy: No secrets in logs, PII handling rules by provenance; audits via config.
- Cost: Optimize MQ throughput and MySQL indices; track via infra dashboards.

Measurement
- SLIs: HTTP latency (adapter), MQ lag, DB query time, error rates.
- Tools: SkyWalking traces, Micrometer metrics, logs with correlation IDs.

