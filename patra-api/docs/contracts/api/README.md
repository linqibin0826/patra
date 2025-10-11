API Contracts

- Primary generation: prefer Maven plugin generation to `docs/contracts/api/openapi.yaml`.
- If manual, keep a single source of truth and document the regeneration command here.

Regeneration (example)
- mvn -pl patra-<service>-adapter -DskipTests springdoc-openapi:generate

Artifacts
- openapi.yaml — consolidated API surface (if generated)

