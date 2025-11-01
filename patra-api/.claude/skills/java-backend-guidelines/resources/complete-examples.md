# Complete Examples from Papertrace

**Purpose**: Full feature implementations showing all layers working together.

## Examples to Include

1. **Provenance Management Feature**
   - Adapter: ProvenanceController
   - Application: ProvenanceManagementOrchestrator
   - Domain: Provenance aggregate
   - Infrastructure: ProvenanceRepositoryImpl

2. **Plan Ingestion Feature**
   - Adapter: PlanController
   - Application: PlanIngestionOrchestrator + Coordinators
   - Domain: BatchPlan, Slice aggregates
   - Infrastructure: BatchPlanRepositoryImpl

3. **Outbox Relay Feature**
   - Application: RelayLeaseCoordinator, RelayPublishCoordinator
   - Domain: OutboxMessage, OutboxRelayLog
   - Infrastructure: OutboxMessageRepositoryImpl

**📝 Status**: Placeholder. Real code examples from patra-ingest to be extracted and documented.
