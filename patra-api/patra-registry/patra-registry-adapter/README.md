# patra-registry-adapter

**Role**: Driving Adapters (Hexagonal Architecture - Adapter Layer)

This module contains **ONLY driving adapters** that receive external triggers and translate them into use case invocations.

## Architectural Contract

- **Direction**: External World → System
- **Responsibility**: Receive external requests, validate input, delegate to application orchestrators
- **Never**: Directly call external resources (databases, external APIs, MQ publishers) - those belong in `patra-registry-infra`

## Module Separation

In Papertrace's Hexagonal Architecture:

```
patra-registry-adapter/     ← Driving Adapters (inbound, receive external triggers)
patra-registry-infra/       ← Driven Adapters (outbound, access external resources)
```

This module-level separation ensures clear boundaries between driving and driven adapters.

## Package Organization

```
adapter/
└── rest/               - REST API endpoints
    ├── ProvenanceEndpointImpl.java    - Provenance management API
    ├── ExprEndpointImpl.java          - Expression compilation API
    └── converter/                     - API DTO converters
```

## Naming Conventions

- **Controllers**: `*EndpointImpl` (REST endpoint implementations)
- **Converters**: `*ApiConverter` (API DTO conversion)

## Driven Adapters (Outbound)

ALL driven adapters belong in `patra-registry-infra`, including:
- Database access → `infra/repository/`
- External API clients → `infra/integration/`
- Cache access → `infra/cache/`

**Never add driven adapters to this module.**

## Related Documentation

- Architecture: `/docs/ARCHITECTURE.md`
- Development Guide: `/docs/DEV-GUIDE.md`
- Agent Guidelines: `/.claude/AGENTS-architecture.md`

## Author

linqibin

## Since

0.1.0
