Title: Adopt Hexagonal Architecture with DDD
Status: Accepted
Date: 2025-10-11

Context
- We require clear separation of concerns, testable domain logic, and controlled integration with frameworks and infrastructure across multiple services.
- Team skills align with Spring Boot and Java, and we aim to maintain high cohesion in the domain while keeping adapters/infra replaceable.

Decision
- Use Hexagonal Architecture (Ports and Adapters) guided by DDD principles:
  - `adapter → app → domain ← infra` dependency direction.
  - Domain modules are pure Java: no Spring or framework dependencies.
  - Application orchestrates use cases and transactions; no business rules.
  - Infrastructure implements repositories, RPC, messaging, and mappings.
  - Adapters expose controllers, schedulers, and listeners; validate input and map errors.

Alternatives
- Layered architecture (controller-service-repository) — simpler but risks domain leakage and weak boundaries.
- Clean architecture — similar goals; our naming and module structure already align closely with hexagonal.

Consequences
- Benefits: high testability, clear boundaries, easier replacement of infra, improved readability.
- Costs: more modules and interfaces; initial overhead in defining ports and mappers.

References
- `docs/architecture/Module-Map.md`
- Repository guidelines in `AGENTS.md` and root `README.md`.

