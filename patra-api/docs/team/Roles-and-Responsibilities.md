# Team Roles and Responsibilities

## System Architect
- Owns architecture decisions (ADRs), C4 diagrams, NFRs, and service boundaries.
- Curates roadmap and backlog; aligns contracts across services.
- Reviews service README changes and contracts for consistency.

## Development Engineer
- Implements features per ADRs/designs and updates service READMEs and contracts.
- Owns runbooks and operational notes for services they change.
- Collaborates with QA to ensure test plans and acceptance criteria are covered.

## Test Engineer
- Owns test strategy, module test plans, and QA sign-off.
- Maintains traceability matrix (requirements ↔ tests) and quality gates.
- Coordinates performance, resilience, and integration testing across services.

## RACI (Selected Artifacts)
- ADRs: Architect (A/R), Dev (C), Test (C)
- Service README: Dev (A/R), Architect (C), Test (I)
- API/Event Contracts: Architect (A), Dev (R), Test (C)
- Test Plans: Test (A/R), Dev (C), Architect (I)
- Runbooks: Dev (A/R), Test (C), Architect (I)
- Roadmap/Backlog: Architect (A/R), Dev (C), Test (C)

## Ownership by Documentation Area (RACI)

Legend: A = Accountable, R = Responsible, C = Consulted, I = Informed

| Doc Area | Path(s) | A | R | C | I |
|---|---|---|---|---|---|
| Architecture | docs/architecture/* | Architect | Architect | Dev, Test | — |
| ADRs | docs/adr/* | Architect | Architect | Dev, Test | — |
| Services Catalog | docs/services/index.md | Architect | Dev | Test | — |
| Service READMEs | patra-<service>/README.md | Dev | Dev | Architect | Test |
| API Contracts | docs/contracts/api/* | Architect | Dev (service owner) | Test | All |
| Event Contracts | docs/contracts/events/* | Architect | Dev (producer owner) | Test | Consumers |
| API Module READMEs | */*-api/README.md | Architect | Dev (module) | Test | — |
| NFR Matrix | docs/nfr/NFR-Matrix.md | Architect | Architect | Dev, Test | — |
| Observability | docs/observability/* | Architect | Dev (service owner) | Test | — |
| Operations Runbooks | docs/operations/* | Dev | Dev | Test | Architect |
| Testing Strategy | docs/testing/README.md | Test | Test | Dev | Architect |
| Test Artifacts/Templates | docs/testing/*.md | Test | Test | Dev | Architect |
| Conventions | docs/conventions/README.md | Architect | Architect | Dev | — |
| Process/DoD | docs/process/* | Architect | Architect | Test | Dev |
| Delivery (Roadmap/Backlog) | docs/delivery/* | Architect | Architect | Dev, Test | — |
| Team | docs/team/* | Architect | Architect | Dev, Test | — |
| Release | docs/release/*, docs/operations/Release-Versioning.md | Architect | Architect | Test | Dev |
