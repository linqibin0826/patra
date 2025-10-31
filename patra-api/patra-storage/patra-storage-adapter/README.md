# patra-storage-adapter

**Role**: Driving Adapters (Hexagonal Architecture - Adapter Layer)

This module contains **ONLY driving adapters** that receive external triggers and translate them into use case invocations.

## Architectural Contract

- **Direction**: External World → System
- **Responsibility**: Receive external requests, validate input, delegate to application orchestrators
- **Never**: Directly call external resources (databases, external APIs, object storage) - those belong in `patra-storage-infra`

## Module Separation

In Papertrace's Hexagonal Architecture:

```
patra-storage-adapter/     ← Driving Adapters (inbound, receive external triggers)
patra-storage-infra/       ← Driven Adapters (outbound, access external resources)
```

This module-level separation ensures clear boundaries between driving and driven adapters.

## Package Organization

```
adapter/
└── rest/                 - REST API endpoints
    └── internal/         - Internal microservice-to-microservice APIs
        └── StorageEndpointImpl.java
```

### API Audience Organization

The `rest/` package can be organized by API audience:
- **`internal/`**: Microservice-to-microservice communication (Feign clients)
- **`public/`**: External-facing public APIs (future)

This organization clarifies the intended consumers of each API.

## Naming Conventions

- **Controllers**: `*EndpointImpl` (REST endpoint implementations)

## Driven Adapters (Outbound)

ALL driven adapters belong in `patra-storage-infra`, including:
- Database access → `infra/repository/`
- Object storage (S3/MinIO) → `infra/storage/`
- External service clients → `infra/integration/`

**Never add driven adapters to this module.**

## Related Documentation

- Architecture: `/docs/ARCHITECTURE.md`
- Development Guide: `/docs/DEV-GUIDE.md`
- Agent Guidelines: `/.claude/AGENTS-architecture.md`

## Author

linqibin

## Since

0.1.0
