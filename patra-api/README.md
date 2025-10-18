# Papertrace — Medical Literature Data Platform

> **Status**: 🚧 Bootstrap Phase (v0.1.0-SNAPSHOT)
> **Architecture**: Microservices + Hexagonal + DDD + Event-Driven
> **Tech Stack**: Java 21, Spring Boot 3.x, MyBatis-Plus, MySQL 8.x

---

## 📖 What is Papertrace?

Papertrace is a **medical literature data platform** designed to:

1. **Collect** literature from 10+ external sources (PubMed, EPMC, Crossref, etc.)
2. **Parse & Standardize** raw data into unified schemas
3. **Store & Index** for efficient search and analysis
4. **Provide APIs** for downstream applications (search, recommendations, analytics)

**Current Focus**: Ensure **reliable data landing** — Collection → Parsing → Storage with idempotency, retry, and observability.

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│           API Gateway (patra-gateway-boot)               │
│         Ingress: Routing • Auth • Rate Limiting          │
└────────────┬──────────────────────────────┬──────────────┘
             │                              │
    ┌────────▼────────────┐        ┌────────▼────────────┐
    │  patra-registry     │◀───────│   patra-ingest      │
    │  (SSOT Registry)    │        │   (Data Collector)  │
    └─────────────────────┘        └──────────┬──────────┘
      • Provenance configs                    │
      • Expression metadata                   │
      • Dictionary management                 │
                                              │
                                     ┌────────▼──────────┐
                                     │ patra-egress-     │
                                     │   gateway         │
                                     │ (Southbound)      │
                                     └────────┬──────────┘
                                              │
                                              ▼
                                      External APIs
                                (PubMed, EPMC, Crossref, etc.)
```

### Key Concepts

- **Provenance**: External data source (PubMed, Crossref) with operational configs
- **Plan**: Blueprint for data collection (window + slicing strategy)
- **Task**: Atomic work unit (e.g., fetch records 1-1000 from PubMed)
- **Outbox**: Transactional event publishing for reliable async communication
- **Cursor**: Watermark tracking for incremental collection

See [Architecture Documentation](./docs/ARCHITECTURE.md) for deep dive.

---

## 📦 Project Structure

### Microservices

| Module | Purpose | Entry Point |
|--------|---------|-------------|
| [**patra-registry**](./patra-registry/README.md) | SSOT for provenance configs, expressions, dictionaries | `patra-registry-boot` |
| [**patra-ingest**](./patra-ingest/README.md) | Orchestrates collection plans, manages task lifecycle | `patra-ingest-boot` |
| [**patra-gateway-boot**](./patra-gateway-boot/README.md) | API Gateway (ingress) with routing and auth | `patra-gateway-boot` |
| [**patra-egress-gateway**](./patra-egress-gateway/README.md) | Southbound gateway for all outbound external service calls | `patra-egress-gateway-boot` |

### Shared Libraries

| Module | Purpose |
|--------|---------|
| **patra-common** | Base classes (AggregateRoot, DomainEvent), error codes, enums |
| **patra-expr-kernel** | Expression engine for dynamic API parameter mapping |
| **patra-spring-boot-starter-core** | Core auto-config (Jackson, error handling) |
| **patra-spring-boot-starter-web** | Web auto-config (REST, Feign, tracing) |
| **patra-spring-boot-starter-mybatis** | MyBatis-Plus auto-config |
| **patra-spring-boot-starter-provenance** | Provenance config integration |
| **patra-spring-cloud-starter-feign** | Feign client enhancements |

### Build Infrastructure

| Module | Purpose |
|--------|---------|
| **patra-parent** | Parent POM with dependency management |

---

## 🚀 Quick Start

### Prerequisites

- **Java 21+** (OpenJDK or Oracle JDK)
- **Maven 3.9+**
- **MySQL 8.0+**
- **Docker & Docker Compose** (for local infrastructure)

### 1. Start Local Infrastructure

```bash
cd docker
docker-compose up -d
```

This starts:
- MySQL (port 3306)
- Nacos (port 8848) — Config center
- RocketMQ (ports 9876, 10911) — Message queue

### 2. Initialize Database

```bash
# Run SQL scripts (location TBD)
mysql -h127.0.0.1 -uroot -p < scripts/init-registry.sql
mysql -h127.0.0.1 -uroot -p < scripts/init-ingest.sql
```

### 3. Build Project

```bash
mvn clean install -DskipTests
```

### 4. Start Services

```bash
# Terminal 1: Start registry
cd patra-registry/patra-registry-boot
mvn spring-boot:run

# Terminal 2: Start ingest
cd patra-ingest/patra-ingest-boot
mvn spring-boot:run

# Terminal 3: Start gateway
cd patra-gateway-boot
mvn spring-boot:run
```

### 5. Verify Services

```bash
# Health check
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Registry
curl http://localhost:8082/actuator/health   # Ingest
```

## 🔧 Environment Profiles

- Spring profiles: `dev` (default) for local Docker Compose stack and `prod` reserved for future cloud deployment. Base configs set `SPRING_PROFILES_ACTIVE` to `dev` by default; export `SPRING_PROFILES_ACTIVE=prod` (or another profile) when you deploy.
- Each `*-boot` module now uses `application.yml` + `application-{profile}.yml`. Put shared settings (ports, starters, Jackson) in the base file, override environment specifics (datasources, redis, logging) in the profile file.
- Config center (Nacos) DataIds follow `<service-name>-<profile>.yaml` plus optional shared `papertrace-<profile>.yaml`. Namespace/group come from `NACOS_NAMESPACE_ID`/`NACOS_CONFIG_GROUP` (fallbacks keep the old `NACOS_NAMESPACE`/`NACOS_GROUP` values).
- Sensitive values (DB/Redis credentials, API keys) live in environment variables consumed inside `application-prod.yml` (e.g., `REGISTRY_DB_URL`, `INGEST_DB_URL`). Commit dev defaults only for local bootstrap.

---

## 📚 Documentation

### Core Guides

- [**Architecture**](./docs/ARCHITECTURE.md) — Hexagonal + DDD principles, dependency rules, design patterns
- [**Development Guide**](./docs/DEV-GUIDE.md) — Code recipes for adding use cases, aggregates, endpoints
- [**CLAUDE.md**](CLAUDE.md) — Instructions for AI assistants working with this codebase

### Module READMEs

- [patra-registry README](./patra-registry/README.md) — Registry service deep dive
- [patra-ingest README](./patra-ingest/README.md) — Ingest service deep dive

---

## 🛠️ Development

### Architecture Principles

1. **Hexagonal Architecture**: Domain at center, adapters at edges
2. **DDD**: Aggregates enforce invariants, domain events capture state changes
3. **CQRS**: Separate read/write models for scalability
4. **Event-Driven**: Async communication via Outbox pattern
5. **Idempotency**: Safe retries via business keys

### Dependency Rules ⚠️

```
adapter  →  app + api
app      →  domain + patra-common
infra    →  domain
domain   →  ONLY patra-common (NO frameworks)
```

**Critical**: Domain layer = **Pure Java** (no Spring, no MyBatis annotations)

### Code Patterns

**Adding a use case?** See [DEV-GUIDE § Adding a New Use Case](./docs/DEV-GUIDE.md#1-adding-a-new-use-case)

**Adding an aggregate?** See [DEV-GUIDE § Adding a New Aggregate](./docs/DEV-GUIDE.md#2-adding-a-new-aggregate)

**Adding an endpoint?** See [DEV-GUIDE § Adding a New REST Endpoint](./docs/DEV-GUIDE.md#5-adding-a-new-rest-endpoint)

---

## 🧪 Testing

```bash
# Unit tests (domain layer)
mvn test -pl patra-{service}-domain

# Integration tests (repository layer)
mvn verify -pl patra-{service}-infra

# API tests (adapter layer)
mvn verify -pl patra-{service}-adapter
```

---

## 📊 Current Status

### ✅ Completed

- ✅ Hexagonal architecture scaffolding
- ✅ Domain models (Provenance, Plan, Task aggregates)
- ✅ Registry CRUD APIs (Feign-based)
- ✅ Plan orchestration (window resolution, slicing, task generation)
- ✅ Outbox pattern implementation
- ✅ Cursor watermark tracking
- ✅ Idempotency via business keys

### 🚧 In Progress

- 🚧 PubMed batch planner (recent commit)
- 🚧 Task execution workers
- 🚧 Data parsing and cleansing pipeline

### 📋 Planned

- 📋 Additional provenances (EPMC, Crossref, etc.)
- 📋 Data storage microservice (`patra-data`)
- 📋 Search microservice with Elasticsearch
- 📋 Observability (metrics, distributed tracing)
- 📋 Admin UI

---

## 🤝 Contributing

### Development Workflow

1. **Create feature branch**: `git checkout -b feat/your-feature`
2. **Follow 7-step process** (see [DEV-GUIDE](./docs/DEV-GUIDE.md))
3. **Ensure compilation**: `mvn clean compile -DskipTests`
4. **Submit PR** with minimal diff

### Code Style

- **Java 21 features**: Use `record`, pattern matching, sealed classes where appropriate
- **Naming**: `*Aggregate`, `*Orchestrator`, `*RepositoryMpImpl`
- **Logging**: Parameterized, English-only (`log.info("Processing {}", id)`)
- **Immutability**: Prefer `record` and `final` fields

---

## 📞 Support

- **Issues**: https://github.com/yourorg/papertrace/issues
- **Discussions**: https://github.com/yourorg/papertrace/discussions
- **Email**: dev@papertrace.io

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Spring Team** — For the excellent Spring Boot/Cloud ecosystem
- **MyBatis Team** — For flexible SQL mapping
- **DDD Community** — For timeless architectural patterns

---

**Built with ❤️ by the Papertrace Team**

Last Updated: 2025-01-12
