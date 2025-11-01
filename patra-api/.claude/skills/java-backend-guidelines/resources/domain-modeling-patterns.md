# Domain Modeling Patterns

**Purpose**: DDD tactical patterns for pure business logic modeling.

---

## Content Outline

1. **Aggregates**
   - Consistency boundaries
   - Aggregate root design
   - Example: BatchPlan, Provenance

2. **Entities**
   - Identity-based equality
   - Lifecycle management
   - Example: Slice, Task

3. **Value Objects**
   - Use `record` for immutables
   - Equality by value
   - Example: LiteratureId, ProvenanceId

4. **Domain Events**
   - Event naming (past tense)
   - Event publishing
   - Example: PlanCompletedEvent

5. **Ports (Interfaces)**
   - Repository ports
   - Service ports
   - Example: ProvenancePort, LiteraturePort

6. **Factories**
   - Complex object creation
   - Example: OutboxMessageFactory

7. **Domain Services**
   - Stateless operations
   - Cross-aggregate logic

---

## Key Principles

- ✅ Pure Java (Lombok, Hutool allowed)
- ✅ Business rules in domain methods
- ✅ Domain events for cross-aggregate communication
- ❌ NO Spring annotations
- ❌ NO framework dependencies

---

**📝 Status**: Content outline created. Full examples from Papertrace domain model (BatchPlan, Provenance, Slice) to be added.

**See Also**: [architecture-overview.md](architecture-overview.md) for domain layer details.
