# patra-common — Shared Domain Foundation

> **Pure Java library** providing base classes, utilities, and domain contracts shared across all Papertrace microservices.

---

## 📌 Purpose

`patra-common` serves as the **foundation layer** for domain-driven design across the platform, providing:

1. **Domain Base Classes**: `AggregateRoot`, `DomainEvent`, `ReadOnlyAggregate`
2. **Error Handling**: Exception hierarchy, error codes, problem detail traits
3. **Shared Enums**: `ProvenanceCode`, `Priority`, `SortDirection`, etc.
4. **JSON Utilities**: Jackson helpers, JSON normalization
5. **Common Utilities**: Hashing, messaging contracts

**Key Principle**: **ZERO framework dependencies** — Pure Java for maximum portability.

---

## 📦 Module Structure

```
patra-common/
└─ src/main/java/com/patra/common/
   ├─ domain/                    # DDD base classes (⭐ core)
   │  ├─ AggregateRoot.java           # Base for aggregate roots
   │  ├─ ReadOnlyAggregate.java       # Base for read models (CQRS)
   │  └─ DomainEvent.java             # Domain event interface
   │  └─ README.md                    # 📖 Package documentation
   │
   ├─ error/                     # Exception hierarchy
   │  ├─ DomainException.java         # Domain layer exceptions
   │  ├─ ApplicationException.java    # App layer exceptions
   │  ├─ problem/                     # Problem detail support
   │  ├─ trait/                       # Error traits (categorization)
   │  └─ codes/                       # Error code definitions
   │
   ├─ enums/                     # Shared enumerations
   │  ├─ ProvenanceCode.java          # Data source codes
   │  ├─ Priority.java                # Task priority levels
   │  ├─ SortDirection.java           # ASC/DESC
   │  └─ ...
   │
   ├─ json/                      # JSON utilities
   │  ├─ JsonMapperHolder.java        # Jackson ObjectMapper singleton
   │  ├─ JsonNormalizer.java          # JSON normalization utils
   │  └─ JsonNodeMappings.java        # JsonNode helpers
   │
   ├─ messaging/                 # Messaging contracts
   │  └─ ChannelKey.java              # Message channel identifiers
   │
   └─ util/                      # General utilities
      └─ HashUtils.java                # Hash generation (SHA-256, etc.)
```

---

## 🔑 Key Components

### 1. Domain Base Classes

**Location**: [`domain/`](src/main/java/com/patra/common/domain/)

**Purpose**: Foundation for DDD aggregates and events.

| Class | Purpose | Used By |
|-------|---------|---------|
| **AggregateRoot<ID>** | Base for aggregate roots (mutable state) | PlanAggregate, TaskAggregate, ... |
| **ReadOnlyAggregate<ID>** | Base for read models (CQRS read side) | ProvenanceConfiguration |
| **DomainEvent** | Interface for domain events | TaskQueuedEvent, PlanCompletedEvent, ... |

**See**: [Domain Package README](src/main/java/com/patra/common/domain/README.md) for comprehensive guide.

**Example Usage**:
```java
// Define aggregate
public class PlanAggregate extends AggregateRoot<Long> {
    private PlanStatus status;

    public void startSlicing() {
        this.status = PlanStatus.SLICING;
        addDomainEvent(new PlanSlicingStartedEvent(getId(), Instant.now()));
    }
}

// Define event
public record PlanSlicingStartedEvent(Long planId, Instant occurredAt)
    implements DomainEvent {

    @Override
    public String eventType() {
        return "ingest.plan.slicing-started";
    }
}
```

---

### 2. Error Handling

**Location**: [`error/`](src/main/java/com/patra/common/error/)

**Purpose**: Structured exception hierarchy with error codes and traits.

#### Exception Hierarchy

```
Throwable
└─ RuntimeException
   ├─ DomainException           # Domain layer (pure business logic)
   │  └─ PlanValidationException, TaskNotFoundException, ...
   │
   └─ ApplicationException       # App layer (orchestration)
      └─ PlanAssemblyException, ConfigurationException, ...
```

**Pattern**:
```java
// Domain exception
public class TaskNotFoundException extends DomainException {
    public TaskNotFoundException(String message) {
        super(ErrorCodes.TASK_NOT_FOUND, message);
    }
}

// App exception
public class PlanAssemblyException extends ApplicationException {
    public PlanAssemblyException(String message, Throwable cause) {
        super(ErrorCodes.PLAN_ASSEMBLY_FAILED, message, cause);
    }
}
```

#### Error Codes

**Location**: [`error/codes/`](src/main/java/com/patra/common/error/codes/)

**Interface**: `ErrorCodeLike`

```java
public interface ErrorCodeLike {
    String getCode();          // Machine-readable code
    String getDefaultMessage(); // Human-readable message
    int getHttpStatus();       // HTTP status code
}
```

**Implementation**:
```java
public enum HttpStdErrors implements ErrorCodeLike {
    NOT_FOUND("NOT_FOUND", "Resource not found", 404),
    CONFLICT("CONFLICT", "Resource already exists", 409),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed", 400);

    // ...
}
```

#### Error Traits

**Location**: [`error/trait/`](src/main/java/com/patra/common/error/trait/)

**Purpose**: Categorize errors for cross-cutting concerns (retry, alerting, etc.).

**Example**:
```java
public interface ErrorTrait { }

public interface Retryable extends ErrorTrait { }
public interface NotRetryable extends ErrorTrait { }
public interface ClientError extends ErrorTrait { }
public interface ServerError extends ErrorTrait { }

// Usage
public class NetworkTimeoutException extends ApplicationException implements Retryable { }
public class InvalidInputException extends ApplicationException implements ClientError, NotRetryable { }
```

---

### 3. Shared Enumerations

**Location**: [`enums/`](src/main/java/com/patra/common/enums/)

**Purpose**: Type-safe constants shared across services.

| Enum | Purpose | Values |
|------|---------|--------|
| **ProvenanceCode** | Data source identifiers | PUBMED, EPMC, CROSSREF, ... |
| **Priority** | Task priority levels | HIGH, NORMAL, LOW |
| **SortDirection** | Sort order | ASC, DESC |
| **IngestDateType** | Date type for ingestion | CREATED, UPDATED, PUBLISHED |
| **RegistryConfigScope** | Config scope level | SOURCE, TASK |

**Example**:
```java
public enum ProvenanceCode {
    PUBMED("pubmed", "PubMed"),
    EPMC("epmc", "Europe PMC"),
    CROSSREF("crossref", "Crossref");

    private final String code;
    private final String displayName;

    // Constructor, getters...
}
```

---

### 4. JSON Utilities

**Location**: [`json/`](src/main/java/com/patra/common/json/)

**Purpose**: Jackson helpers for JSON serialization/deserialization.

| Class | Purpose |
|-------|---------|
| **JsonMapperHolder** | Singleton ObjectMapper for global JSON operations |
| **JsonNormalizer** | Normalize JSON strings (sorting keys, removing whitespace) |
| **JsonNodeMappings** | Helpers for working with JsonNode |

**Example**:
```java
// Serialize to JSON
String json = JsonMapperHolder.INSTANCE.writeValueAsString(object);

// Deserialize from JSON
MyClass obj = JsonMapperHolder.INSTANCE.readValue(json, MyClass.class);

// Normalize JSON (for hashing/comparison)
String normalized = JsonNormalizer.normalize(json);
```

---

### 5. Utilities

**Location**: [`util/`](src/main/java/com/patra/common/util/)

#### HashUtils

**Purpose**: Generate hashes for idempotency keys, snapshots, etc.

**Methods**:
```java
public class HashUtils {
    // SHA-256 hash of string
    public static String sha256(String input);

    // SHA-256 hash of multiple strings (concatenated)
    public static String sha256(String... inputs);

    // MD5 hash (for non-security purposes)
    public static String md5(String input);
}
```

**Usage**:
```java
// Generate plan key
String planKey = HashUtils.sha256(
    provenanceCode,
    operationCode,
    windowSpec.toString(),
    strategyCode
);
```

---

## 🔗 Dependencies

**This module has ZERO external dependencies** (except JDK and Jackson for JSON).

**POM**:
```xml
<dependencies>
    <!-- ONLY Jackson for JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- NO Spring, NO MyBatis, NO Lombok (domain classes) -->
</dependencies>
```

**Why minimal dependencies?**
- ✅ **Portability**: Can be used in any Java project
- ✅ **Fast builds**: No transitive dependency hell
- ✅ **Clear boundaries**: Domain logic isolated from frameworks

---

## 🔌 Usage in Other Modules

### Domain Layers

**All `*-domain` modules depend on patra-common**:

```xml
<!-- patra-ingest-domain/pom.xml -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
</dependency>
```

**Usage**:
```java
// Extend base classes
public class TaskAggregate extends AggregateRoot<Long> { }

// Use shared enums
ProvenanceCode provenance = ProvenanceCode.PUBMED;

// Throw domain exceptions
throw new TaskNotFoundException("Task not found: " + taskId);
```

### Application Layers

**App layers use error handling and utilities**:

```java
// Map domain exceptions to app exceptions
try {
    task.markRunning();
} catch (IllegalStateException e) {
    throw new TaskExecutionException("Cannot start task", e);
}

// Use hash utils
String idempotentKey = HashUtils.sha256(planId, sliceId, batchId);
```

---

## 🛠️ Extending

### Adding a New Shared Enum

**Recipe**:

1. Create enum in `enums/` package:
```java
package com.patra.common.enums;

public enum TaskPriority {
    URGENT(1),
    HIGH(2),
    NORMAL(3),
    LOW(4);

    private final int level;

    TaskPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
```

2. Use in domain models:
```java
public class TaskAggregate extends AggregateRoot<Long> {
    private TaskPriority priority;

    public void escalate() {
        if (priority == TaskPriority.NORMAL) {
            priority = TaskPriority.HIGH;
        }
    }
}
```

### Adding a New Error Code

**Recipe**:

1. Create enum in `error/codes/`:
```java
package com.patra.common.error.codes;

public enum IngestErrors implements ErrorCodeLike {
    PLAN_NOT_FOUND("PLAN_NOT_FOUND", "Plan not found", 404),
    TASK_CANCELLED("TASK_CANCELLED", "Task was cancelled", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    // Constructor, getters, implementation of ErrorCodeLike...
}
```

2. Use in exceptions:
```java
public class PlanNotFoundException extends DomainException {
    public PlanNotFoundException(String message) {
        super(IngestErrors.PLAN_NOT_FOUND, message);
    }
}
```

---

## 🧪 Testing

### No Tests in patra-common (By Design)

**Why?**
- **Pure utilities**: Simple, self-evident code
- **Tested via usage**: Domain tests in `*-domain` modules exercise base classes
- **Minimal logic**: Enums, base classes have no complex business logic

**Exception**: Add tests if complex logic is introduced (e.g., advanced hash algorithms).

---

## 📊 Module Statistics

| Metric | Count |
|--------|-------|
| **Java Classes** | ~20 |
| **Lines of Code** | ~800 |
| **Dependencies** | 1 (Jackson) |
| **Dependent Modules** | All (registry, ingest, gateway, ...) |

---

## 🚀 Best Practices

### DO ✅

- **Keep pure**: NO Spring, MyBatis, or framework dependencies
- **Keep small**: Only truly shared concepts (3+ modules using it)
- **Document well**: Add Javadoc for public APIs

### DON'T ❌

- **Don't add service-specific logic**: Belongs in `*-domain` modules
- **Don't add infrastructure code**: Belongs in `*-infra` or starters
- **Don't add Spring annotations**: Violates framework independence

---

## 🔗 Related Documentation

- [Domain Package README](src/main/java/com/patra/common/domain/README.md) — Deep dive into DDD base classes
- [Architecture Guide](../docs/ARCHITECTURE.md) — Hexagonal Architecture principles
- [Development Guide](../docs/DEV-GUIDE.md) — How to use base classes

---

**Last Updated**: 2025-01-12
