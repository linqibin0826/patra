# patra-ingest-adapter

**Role**: Driving Adapters (Hexagonal Architecture - Adapter Layer)

This module contains **ONLY driving adapters** that receive external triggers and translate them into use case invocations.

## Architectural Contract

- **Direction**: External World → System
- **Responsibility**: Receive external requests, validate input, delegate to application orchestrators
- **Never**: Directly call external resources (databases, external APIs, MQ publishers) - those belong in `patra-ingest-infra`

## Module Separation

In Papertrace's Hexagonal Architecture:

```
patra-ingest-adapter/     ← Driving Adapters (inbound, receive external triggers)
patra-ingest-infra/       ← Driven Adapters (outbound, access external resources)
```

This module-level separation ensures clear boundaries between driving and driven adapters.

## Package Organization

```
adapter/
├── scheduler/        - XXL-Job scheduled tasks
│   ├── config/       - XXL-Job executor configuration
│   ├── job/          - Scheduled job implementations
│   └── param/        - Job parameter DTOs
└── stream/          - RocketMQ message consumers
    ├── IngestStreamConsumers.java
    └── dto/          - Message payload DTOs
```

## Naming Conventions

- **Jobs**: `*Job` (e.g., `PubmedHarvestJob`)
- **Consumers**: `*Consumers` (e.g., `IngestStreamConsumers`)
- **Controllers**: `*Controller` (future REST APIs)

## Driven Adapters (Outbound)

ALL driven adapters belong in `patra-ingest-infra`, including:
- Database access → `infra/repository/`
- External API clients → `infra/integration/pubmed/`, `infra/integration/registry/`
- MQ publishers → `infra/messaging/`
- Future: Webhooks, event publishers, monitoring clients

**Never add driven adapters to this module.**

## Related Documentation

- Architecture: `/docs/ARCHITECTURE.md`
- Development Guide: `/docs/DEV-GUIDE.md`
- Agent Guidelines: `/.claude/AGENTS-architecture.md`

## Author

linqibin

## Since

0.1.0
