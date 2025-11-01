# Orchestrator & Coordinator Patterns

**Purpose**: Application layer patterns for use case coordination and transaction management.

---

## Content Outline

1. **Pattern 1: Orchestrator + Coordinators**
   - When to use: Single transaction, complex internal flow
   - Structure: Main Orchestrator + multiple Coordinators
   - Example: PlanIngestionOrchestrator

2. **Pattern 2: Main Orchestrator + Sub-UseCases**
   - When to use: Multi-phase transactions, external API calls
   - Structure: Main UseCase + independent Sub-UseCases
   - Example: TaskExecutionUseCase

3. **Transaction Management**
   - `@Transactional` at orchestrator level
   - NEVER call external APIs within transactions
   - Minimize transaction scope

4. **Coordinator Patterns**
   - PersistenceCoordinator
   - IdempotencyCoordinator
   - PublishingCoordinator
   - ValidationCoordinator

---

## Key Principles

- ✅ Orchestrate only, delegate business logic to Domain
- ✅ Manage transactions (@Transactional)
- ✅ Keep transactions short
- ❌ NO business rules (belong in Domain)

---

**📝 Status**: Content outline created. Full implementation patterns from PlanIngestionOrchestrator and TaskExecutionUseCase to be added.

**See Also**: [architecture-overview.md](architecture-overview.md) for application layer details.
