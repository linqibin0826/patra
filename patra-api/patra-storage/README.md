# patra-storage — Object Storage Metadata Service

> Dedicated microservice responsible for recording and managing metadata for files uploaded to external object storage providers.

## 🎯 Responsibilities
- Accept upload record requests from internal services (e.g., patra-ingest)
- Persist file metadata, business context, and lifecycle attributes
- Enforce idempotency via unique `storage_key`
- Offer Feign APIs for other services, no public REST surface

## 🏗 Module Layout
```
patra-storage/
├─ patra-storage-api/        # Feign contracts + DTO
├─ patra-storage-domain/     # Pure Java aggregates, VOs, ports
├─ patra-storage-app/        # Use case orchestrators (@Transactional)
├─ patra-storage-infra/      # MyBatis-Plus repositories, DO + mapper + Flyway
├─ patra-storage-adapter/    # REST controllers implementing internal endpoint
└─ patra-storage-boot/       # Executable Spring Boot application
```

Follow Hexagonal Architecture rules from `AGENTS.md`:
- Adapter → App → Domain ← Infra dependency flow
- Domain layer depends only on `patra-common` + Lombok/Hutool (no Spring)
- Documentation + code style align with Google Java Style

Before editing any module, read this file to understand boundaries and naming conventions.
