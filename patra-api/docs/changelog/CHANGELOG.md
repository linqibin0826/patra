# Changelog

## Unreleased
- Documentation skeleton aligned with docs/docs-spec.md
- Added service catalog, C4 context/container outlines, ADR template
- Initialized contracts directories and NFR/observability/operations/testing guides
- Added ADRs 0001/0002 (Hexagonal+DDD, RocketMQ Outbox)
- Added Deployment Topology and Module Map
- Added Egress Gateway API contract and TaskReady JSON Schema
- Added Ingest Runbook; improved index and service links
- Deferred OpenAPI generation; using per-endpoint Markdown docs
- Expanded NFR and Observability guides
- Added Core Sequences diagrams
- Added Registry and Egress runbooks
- Added Registry internal API contract
- Refined conventions and testing docs
 - Removed proposed OutboxPublished event/docs to reflect current code
 - Added Observability metrics glossary and dashboards; expanded Outbox relay metrics and troubleshooting
 - Rewrote patra-ingest/README.md in Markdown with code-backed references
 - Added patra-ingest-api/README.md (consumer-facing error codes) and linked from services catalog
 - Fixed Ingest runbook to remove outdated OutboxPublished event reference
 - Rewrote patra-egress-gateway/README.md in Markdown with code-backed references
 - Added patra-egress-gateway-api/README.md and linked from services catalog
- Rewrote patra-gateway-boot/README.md in Markdown with routes/config highlights
- Added patra-registry-api/README.md link to services catalog
 - Cleaned Feign starter README properties example (single patra.feign tree)
  - Corrected Egress API contract to match ResilienceConfigDTO (seconds-based fields)
  - Updated ADR template to Markdown sections and bolded metadata
 - Added API module README links to docs/README.md Contracts section
