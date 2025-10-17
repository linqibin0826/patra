# 001-logging-starter — Documentation Map

This folder contains the complete specification and contracts for the Logging Starter. The layout is flattened and consistent so readers can quickly find entry points.

Start Here
- 01-overview/spec.md — Feature Specification (authoritative requirements)
- 01-overview/quickstart.md — Quick Start for adopters

Sections
- 01-overview/ — Context and entry points
  - spec.md — goals, scope, acceptance criteria
  - quickstart.md — how to adopt in a service
- 02-contracts/ — Developer-facing contracts
  - README.md — index of contracts
  - utility-api.md — Java API surface
  - spring-boot-properties.md — configuration properties
  - mdc-fields-reference.md — standard MDC fields
  - integrations/ — HTTP filters, Feign interceptor, AOP sanitizer contracts
- 03-schemas/ — Configuration schemas and examples
  - logging-config.schema.yml — JSON Schema for Nacos config
  - README.md — usage and precedence
  - examples/ — validated YAML examples (common/service/env)
- 04-references/ — Background and supporting material
  - data-model.md — conceptual data model for logs
  - research.md — research notes and rationale
- 05-tasks/ — Work planning and checklists
  - plan.md — milestone plan
  - tasks.md — task board / to‑dos
  - checklists/requirements.md — requirement checklist linked to spec

Conventions
- Numeric prefixes (01‑05) enforce reading order: overview → contracts → schemas → references → tasks.
- Domain layer never depends on frameworks; contracts are framework‑neutral; Spring integration lives in starters.
- All schema examples validate against 03-schemas/logging-config.schema.yml.

Maintenance Tips
- When adding a new contract, place it under 02-contracts and cross‑link to 03-schemas if it introduces config.
- Keep examples under 03-schemas/examples; avoid duplicating snippets in multiple files.
- Update links if relocating files; search for relative links with `rg -n "]("`.
