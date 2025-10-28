# patra-common — Shared Foundation (Multi-Module)

> **Multi-module project** providing core utilities, storage abstractions, and shared models for Papertrace microservices.

---

## 📦 Module Structure

`patra-common` is now a **multi-module aggregator** with three independent submodules:

```
patra-common/                    (聚合 POM - 无代码)
├── patra-common-core/          (核心基础 - 所有服务必需)
├── patra-common-storage/       (存储键生成 - 按需依赖)
└── patra-common-model/         (共享数据模型 - 按需依赖)
```

---

## 🎯 Design Philosophy

### Before (Old Structure)
❌ **Problem**: All microservices were forced to depend on ALL code in `patra-common`, including:
- Object storage key generation (only used by patra-ingest)
- StandardLiterature model (only used by 3 modules)
- This violated "dependency on demand" principle

### After (New Structure)
✅ **Solution**: Modular design with clear boundaries:
- **patra-common-core**: Truly shared core utilities (domain/error/enums/json/util)
- **patra-common-storage**: Storage key generation (optional, DDD business rule)
- **patra-common-model**: Shared data models (optional, inter-service contracts)

---

## 📌 Submodules

### 1. patra-common-core (Required by All Services)

**Artifact**: `com.papertrace:patra-common-core`

**Purpose**: Foundation classes used by ALL Papertrace services.

**Contents**:
- **domain/**: DDD base classes (`AggregateRoot`, `DomainEvent`)
- **error/**: Exception hierarchy, error codes, traits
- **enums/**: Shared enums (`ProvenanceCode`, `Priority`)
- **json/**: JSON utilities (`JsonMapperHolder`, `JsonNormalizer`)
- **messaging/**: Message channel identifiers
- **util/**: Common utilities (`HashUtils`)

**Dependencies**: Hutool, Jackson, SLF4J (provided)

**Usage**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-core</artifactId>
</dependency>
```

**Who Uses**: All `*-domain`, `*-app`, `*-infra`, `*-adapter` modules

---

### 2. patra-common-storage (Optional - On-Demand)

**Artifact**: `com.papertrace:patra-common-storage`

**Purpose**: Standardized object storage key generation strategies.

**Contents**:
- **ObjectKeyContext**: Immutable context for key generation
- **ObjectKeyGenerator**: Strategy interface
- **DatePartitionedKeyGenerator**: Date-based partitioning (yyyy/MM/dd)
- **ObjectKeyTemplate**: Factory methods for common patterns

**Key Format**:
```
{service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}
```

**Example**:
```java
import com.patra.common.storage.ObjectKeyTemplate;

String key = ObjectKeyTemplate.generateDailyKey(
    "ingest", "literature-batch", "pubmed-123-batch-001", "json"
);
// Result: ingest/literature-batch/2025/10/28/pubmed-123-batch-001.json
```

**Dependencies**: Hutool

**Usage**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-storage</artifactId>
</dependency>
```

**Who Uses**:
- `patra-spring-boot-starter-object-storage` (StorageLocationResolver)
- Any service needing standardized storage key generation

**Design Note**: This is a **business rule** (naming convention), not infrastructure code. Keeping it separate from the `object-storage` starter allows domain layers to use standardized naming without depending on Spring framework.

---

### 3. patra-common-model (Optional - On-Demand)

**Artifact**: `com.papertrace:patra-common-model`

**Purpose**: Shared data models for inter-service communication.

**Contents**:
- **StandardLiterature**: Common literature data structure used across services

**Dependencies**: Jackson (for JSON serialization)

**Usage**:
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-model</artifactId>
</dependency>
```

**Who Uses**:
- `patra-ingest-domain` (port interfaces)
- `patra-ingest-app` (orchestrators)
- `patra-spring-boot-starter-provenance` (data adapters)

---

## 🔧 Migration Guide

### For Service Modules

**Before** (old dependencies):
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
</dependency>
```

**After** (choose what you need):
```xml
<!-- Required: Core utilities -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-core</artifactId>
</dependency>

<!-- Optional: If you need storage key generation -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-storage</artifactId>
</dependency>

<!-- Optional: If you need StandardLiterature model -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common-model</artifactId>
</dependency>
```

### Import Statement Changes

**Storage package renamed**:
```java
// Before
import com.patra.common.objectstorage.*;

// After
import com.patra.common.storage.*;
```

**Note**: `StorageContext` and `StorageLocation` have been moved to:
```java
import com.patra.starter.objectstorage.StorageContext;
import com.patra.starter.objectstorage.StorageLocation;
```

---

## 🏗️ Architecture Benefits

### 1. Dependency on Demand ✅
- Services only depend on what they actually use
- Reduces classpath pollution
- Faster builds for services that don't need all features

### 2. Clear Boundaries ✅
- **Core**: Truly universal utilities
- **Storage**: Domain-level naming rules (business logic)
- **Model**: Inter-service contracts

### 3. Hexagonal Architecture Compliance ✅
- Domain layers can use `patra-common-storage` (business rules) without depending on infrastructure (`object-storage` starter)
- Separation of concerns: naming strategy vs. storage implementation

### 4. Independent Evolution ✅
- Each submodule can evolve independently
- Version management flexibility (future)

---

## 🔗 Dependencies

```
patra-common (POM aggregator)
    ↓
    ├─ patra-common-core (Hutool, Jackson, SLF4J)
    │     ↑
    │     └─ All microservice layers
    │
    ├─ patra-common-storage (Hutool)
    │     ↑
    │     └─ patra-spring-boot-starter-object-storage
    │
    └─ patra-common-model (Jackson)
          ↑
          ├─ patra-ingest-domain
          └─ patra-spring-boot-starter-provenance
```

---

## 📊 Module Statistics

| Module | Classes | LOC | Dependencies | Usage |
|--------|---------|-----|--------------|-------|
| **patra-common-core** | ~27 | ~2500 | Hutool, Jackson | All services (required) |
| **patra-common-storage** | 4 | ~300 | Hutool | patra-ingest, object-storage starter |
| **patra-common-model** | 1 | ~200 | Jackson | patra-ingest, patra-provenance |

---

## 🚀 Build Commands

```bash
# Build all submodules
cd patra-common
mvn clean install

# Build specific submodule
cd patra-common-core
mvn clean install

# Verify dependencies
mvn dependency:tree
```

---

## 🔗 Related Documentation

- [ARCHITECTURE.md](../docs/ARCHITECTURE.md) — Hexagonal Architecture principles
- [DEV-GUIDE.md](../docs/DEV-GUIDE.md) — Development guidelines
- [AGENTS-architecture.md](../.claude/AGENTS-architecture.md) — DDD patterns reference

---

**Last Updated**: 2025-10-28
**Migration**: patra-common → multi-module structure (patra-common-core/storage/model)
