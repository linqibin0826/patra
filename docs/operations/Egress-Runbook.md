Egress Gateway Runbook

Purpose
- Operate `patra-egress-gateway`: standardized outbound HTTP with resilience envelope.

How to Run Locally
- Start infra (optional): `cd docker/compose && docker compose up -d`
- Launch service: `./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot -am spring-boot:run`

Health and Diagnostics
- Health: `/actuator/health` (HTTP client, config)
- Logs: traceId, URL, method, statusCode, durationMs
- Metrics: `egress.call.duration`, status counters, retry count

Common Failures
- DNS/HTTP connectivity → verify outbound network, proxy settings
- Timeouts/circuit trips → adjust defaults; inspect per-call overrides usage
- Sensitive headers leakage → confirm sanitizer is applied and configured

Rollback/Recovery
- Revert to prior configuration defaults; monitor error-rate and latency
- Disable retries temporarily if cascading failures observed

