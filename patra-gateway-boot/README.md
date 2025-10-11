Purpose and Responsibilities
- Spring Cloud Gateway entrypoint for Papertrace. Routes external traffic to internal services.

Key Notes
- Entry class: `com.patra.gateway.PatraGatewayApplication`.
- Configuration: `application.yml` (routes, filters, CORS, rate limits). Example routes for `patra-ingest` and `patra-registry` with `StripPrefix`.

Observability
- Propagate trace IDs; ensure access logs include correlation and client info.

Run Locally
- `./mvnw -pl patra-gateway-boot spring-boot:run`
