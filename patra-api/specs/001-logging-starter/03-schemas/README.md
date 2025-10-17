# Logging Config Schema & Examples

This folder contains the validation schema and canonical examples for Nacos‑managed logging configuration.

Files
- `logging-config.schema.yml` — JSON Schema (Draft 2020‑12) for validating `logging-*.yml` files.
- `examples/` — Ready‑to‑use examples for common, service, and environment overlays.

How Precedence Works (highest → lowest)
1. Service‑specific config (e.g., `logging-patra-{service}.yml`)
2. Environment‑specific config (e.g., `logging-production.yml`)
3. Common config (`logging-common.yml`)
4. Application defaults (starter `logback-spring.xml`)

Validation
- Use any JSON Schema validator that supports YAML input or convert YAML → JSON first.
- The schema enforces structure and value ranges. Cross‑field rules like `discardingThreshold <= queueSize` should be validated at runtime with descriptive errors.

Notes
- Keys here target the logging backend (e.g., logback) and are separate from Spring Boot `papertrace.logging.*` properties. See `spring-boot-properties.md` for the application‑level properties.
