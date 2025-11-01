# Transaction & Error Handling

## Overview

Transaction management and error handling are critical cross-cutting concerns in Papertrace. This guide covers Spring's `@Transactional`, exception design, error mapping to HTTP responses, and distributed transaction patterns.

**Core Principle**: Transactions at the orchestrator boundary, domain exceptions flow outward, never call external APIs inside transactions.

---

## Transaction Management

### Transaction Boundaries

**Rule**: Apply `@Transactional` at the **Orchestrator level** ONLY.

**Why**:
- Orchestrators coordinate use cases → natural transaction boundary
- Domain layer is pure Java → cannot use Spring annotations
- Infrastructure layer methods are too granular

### Correct Transaction Placement

```java
// ✅ GOOD: Transaction at orchestrator level
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

    private final PlanPort planPort;
    private final SlicePort slicePort;
    private final OutboxPort outboxPort;

    @Override
    @Transactional  // ← Transaction boundary
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        // All database operations within this method are in one transaction
        BatchPlan plan = assemblePlan(command);
        planPort.save(plan);

        List<Slice> slices = generateSlices(plan);
        slicePort.saveAll(slices);

        OutboxMessage message = createOutboxMessage(plan);
        outboxPort.save(message);

        return new PlanCreationResult(plan.id(), slices.size());
    }
}
```

```java
// ❌ BAD: Transaction at repository level
@Repository
public class PlanRepositoryImpl implements PlanPort {

    @Transactional  // ← NO! Too granular
    public void save(BatchPlan plan) {
        // ...
    }
}

// ❌ BAD: Transaction in domain layer
public class BatchPlan {

    @Transactional  // ← NO! Domain is pure Java
    public void addSlice(Slice slice) {
        // ...
    }
}
```

---

## Transaction Propagation

### Common Propagation Behaviors

| Propagation | Behavior | Use Case |
|-------------|----------|----------|
| **REQUIRED** (default) | Join existing transaction, or create new | Most orchestrators |
| **REQUIRES_NEW** | Always create new transaction, suspend existing | Independent operations, auditing |
| **NESTED** | Nested transaction with savepoint | Partial rollback scenarios |
| **NOT_SUPPORTED** | Execute without transaction | Read-only queries, reporting |
| **MANDATORY** | Must be called within transaction | Internal coordinators |

### Example: REQUIRED (Default)

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

    @Transactional  // Propagation.REQUIRED (default)
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        // All operations in this transaction
    }
}
```

### Example: REQUIRES_NEW (Independent Transaction)

```java
@Service
@RequiredArgsConstructor
public class AuditLogCoordinator {

    // Use REQUIRES_NEW when audit must succeed even if parent transaction rolls back
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(String operation, Object details) {
        AuditLog log = new AuditLog(operation, details, Instant.now());
        auditLogPort.save(log);
    }
}
```

**Use Case**: Even if the main plan creation fails, the audit log is saved.

```java
@Transactional
public void createPlan(CreatePlanCommand command) {
    auditLogCoordinator.logOperation("CREATE_PLAN", command);  // REQUIRES_NEW

    // Main operation
    BatchPlan plan = assemblePlan(command);
    planPort.save(plan);

    // If this throws, plan creation rolls back, but audit log is committed
    if (someCondition) {
        throw new BusinessException("Plan creation failed");
    }
}
```

---

## Transaction Rollback

### Default Rollback Behavior

Spring rolls back transactions on:
- **Unchecked exceptions** (RuntimeException and its subclasses)
- **Errors** (e.g., OutOfMemoryError)

Spring **does NOT** roll back on:
- **Checked exceptions** (Exception subclasses, excluding RuntimeException)

### Explicit Rollback Configuration

```java
// Rollback on specific checked exceptions
@Transactional(rollbackFor = ProvenanceNotFoundException.class)
public void createPlan(CreatePlanCommand command) throws ProvenanceNotFoundException {
    // ...
}

// Do NOT rollback on specific exceptions
@Transactional(noRollbackFor = OptimisticLockException.class)
public void updatePlan(UpdatePlanCommand command) {
    // Retry logic handles OptimisticLockException, no rollback needed
}
```

### Manual Rollback

```java
@Transactional
public void createPlanWithValidation(CreatePlanCommand command) {
    BatchPlan plan = assemblePlan(command);
    planPort.save(plan);

    // Manual rollback on business condition
    if (!plan.isValid()) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        throw new InvalidPlanException("Plan validation failed");
    }
}
```

---

## External API Calls in Transactions

### The Golden Rule

**NEVER call external APIs inside @Transactional methods.**

**Why**:
- External API calls are slow → transaction held open too long
- Network failures → transaction timeout
- Database connection pool exhaustion
- Cannot rollback external API calls

### Anti-Pattern: External API Inside Transaction

```java
// ❌ BAD: External API call inside transaction
@Transactional
public void harvestData(HarvestCommand command) {
    BatchPlan plan = assemblePlan(command);
    planPort.save(plan);  // Transaction starts

    // External API call (slow, unreliable)
    List<Literature> results = pubmedApiClient.search(query);  // ← NO!

    literaturePort.saveAll(results);  // Transaction still open
}
```

**Problem**: If PubMed API is slow (10 seconds), the database transaction is held open for 10+ seconds, blocking other operations.

### Correct Pattern: External API Outside Transaction

```java
// ✅ GOOD: External API call outside transaction
@Service
@RequiredArgsConstructor
public class HarvestOrchestrator {

    @Transactional
    public BatchPlan createPlan(CreatePlanCommand command) {
        BatchPlan plan = assemblePlan(command);
        planPort.save(plan);
        return plan;
    }

    // Separate method, NO @Transactional
    public List<Literature> fetchFromExternalApi(BatchPlan plan) {
        return pubmedApiClient.search(plan.query());  // Outside transaction
    }

    @Transactional
    public void saveLiterature(List<Literature> results) {
        literaturePort.saveAll(results);  // New transaction
    }
}
```

**Workflow**:
1. Create plan (transaction 1)
2. Call external API (no transaction)
3. Save results (transaction 2)

---

## Exception Design

### Exception Hierarchy

```
Throwable
├── Error
└── Exception
    ├── RuntimeException (Unchecked)
    │   ├── DomainException (abstract)
    │   │   ├── ProvenanceNotFoundException
    │   │   ├── InvalidPlanException
    │   │   └── BusinessKeyDuplicateException
    │   ├── ApplicationException (abstract)
    │   │   ├── ProvenanceConfigNotFoundException
    │   │   └── OrchestrationException
    │   └── InfrastructureException (abstract)
    │       ├── RepositoryException
    │       └── ExternalApiException
    └── IOException (Checked)
```

### Domain Exceptions

**Location**: `patra-{service}-domain/src/main/java/.../exception/`

**Purpose**: Business rule violations

```java
package com.patra.registry.domain.exception;

public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Specific Exception**:

```java
public class ProvenanceNotFoundException extends DomainException {
    private final String provenanceCode;

    public ProvenanceNotFoundException(String provenanceCode) {
        super("Provenance not found: " + provenanceCode);
        this.provenanceCode = provenanceCode;
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }
}
```

### Application Exceptions

**Location**: `patra-{service}-app/src/main/java/.../exception/`

**Purpose**: Use case orchestration failures

```java
package com.patra.ingest.app.exception;

public abstract class ApplicationException extends RuntimeException {
    protected ApplicationException(String message) {
        super(message);
    }
}

public class ProvenanceConfigNotFoundException extends ApplicationException {
    public ProvenanceConfigNotFoundException(String provenanceCode) {
        super("Provenance configuration not found for: " + provenanceCode);
    }
}
```

---

## HTTP Error Mapping (ProblemDetail)

### GlobalExceptionHandler

**Location**: `patra-{service}-adapter/src/main/java/.../rest/GlobalExceptionHandler.java`

**Purpose**: Map exceptions to RFC 7807 ProblemDetail responses

```java
package com.patra.registry.adapter.rest;

import com.patra.registry.domain.exception.ProvenanceNotFoundException;
import com.patra.registry.app.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Domain exceptions → 404 Not Found
    @ExceptionHandler(ProvenanceNotFoundException.class)
    public ProblemDetail handleProvenanceNotFound(ProvenanceNotFoundException ex) {
        log.warn("Provenance not found: {}", ex.getProvenanceCode());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problem.setTitle("Provenance Not Found");
        problem.setType(URI.create("https://patra.com/errors/provenance-not-found"));
        problem.setProperty("provenanceCode", ex.getProvenanceCode());
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    // Application exceptions → 422 Unprocessable Entity
    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplicationException(ApplicationException ex) {
        log.error("Application error", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.getMessage()
        );
        problem.setTitle("Application Error");
        problem.setType(URI.create("https://patra.com/errors/application-error"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    // Validation errors → 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://patra.com/errors/validation-error"));

        // Add field errors
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    // Generic exception → 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://patra.com/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }
}
```

### ProblemDetail Response Example

**Request**:
```bash
GET /api/v1/provenances/UNKNOWN
```

**Response** (404 Not Found):
```json
{
  "type": "https://patra.com/errors/provenance-not-found",
  "title": "Provenance Not Found",
  "status": 404,
  "detail": "Provenance not found: UNKNOWN",
  "provenanceCode": "UNKNOWN",
  "timestamp": "2024-11-01T12:00:00Z"
}
```

---

## Distributed Transactions

### Problem

When operations span multiple services or databases:

```
patra-ingest (DB1)      patra-storage (DB2)
     ↓                         ↓
Save BatchPlan          Save Literature
```

If `Save Literature` fails, `Save BatchPlan` is already committed → data inconsistency.

### Solution 1: Eventual Consistency (Recommended)

Use **Outbox Pattern** + **Event-Driven Architecture**.

```java
@Transactional
public void createPlan(CreatePlanCommand command) {
    // 1. Save plan in DB
    BatchPlan plan = assemblePlan(command);
    planPort.save(plan);

    // 2. Save outbox message in SAME transaction
    OutboxMessage message = OutboxMessage.builder()
        .aggregateType("BatchPlan")
        .aggregateId(plan.id().value())
        .eventType("PlanCreated")
        .payload(serializePlan(plan))
        .build();
    outboxPort.save(message);

    // Both plan and outbox message committed atomically
}

// Separate process polls outbox table and publishes to message queue
@Scheduled(fixedDelay = 1000)
public void publishOutboxMessages() {
    List<OutboxMessage> pending = outboxPort.findPending();
    for (OutboxMessage message : pending) {
        messagePublisher.publish(message);  // RocketMQ/Kafka
        outboxPort.markPublished(message.id());
    }
}
```

**Benefits**:
- No distributed transaction overhead
- Eventual consistency
- Fault-tolerant

**See**: [outbox-pattern.md](outbox-pattern.md) for complete details.

---

### Solution 2: Seata (Distributed Transaction Coordinator)

**Use Case**: When strong consistency is required.

```java
@Service
@RequiredArgsConstructor
public class CrossServiceOrchestrator {

    private final PlanPort planPort;  // patra-ingest DB
    private final LiteraturePort literaturePort;  // patra-storage DB

    @GlobalTransactional  // Seata annotation
    public void createPlanAndLiterature(CreateCommand command) {
        // Both operations in distributed transaction
        BatchPlan plan = assemblePlan(command);
        planPort.save(plan);  // DB1

        Literature literature = assembleLiterature(command);
        literaturePort.save(literature);  // DB2

        // If any operation fails, both rollback (2PC)
    }
}
```

**Configuration** (`application.yml`):

```yaml
seata:
  enabled: true
  application-id: patra-ingest
  tx-service-group: patra-tx-group
  service:
    vgroup-mapping:
      patra-tx-group: default
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
```

**Trade-offs**:
- ✅ Strong consistency
- ❌ Performance overhead (2PC)
- ❌ Complexity
- ❌ Single point of failure (Seata server)

**Recommendation**: Use Outbox Pattern for most cases, reserve Seata for critical operations requiring strong consistency.

---

## Best Practices

### 1. Keep Transactions Short

```java
// ❌ BAD: Long-running transaction
@Transactional
public void processHarvest(HarvestCommand command) {
    BatchPlan plan = createPlan(command);
    List<Literature> results = fetchFromApi(command);  // Slow!
    saveLiterature(results);
}

// ✅ GOOD: Short transactions
@Transactional
public BatchPlan createPlan(CreatePlanCommand command) {
    return planPort.save(assemblePlan(command));
}

public List<Literature> fetchFromApi(HarvestCommand command) {
    return apiClient.search(command.query());  // No transaction
}

@Transactional
public void saveLiterature(List<Literature> results) {
    literaturePort.saveAll(results);
}
```

---

### 2. Avoid N+1 Queries in Transactions

```java
// ❌ BAD: N+1 queries
@Transactional
public void updatePlans(List<BatchPlanId> planIds) {
    for (BatchPlanId id : planIds) {
        BatchPlan plan = planPort.findById(id);  // N queries
        plan.updateStatus(PlanStatus.COMPLETED);
        planPort.save(plan);
    }
}

// ✅ GOOD: Batch query
@Transactional
public void updatePlans(List<BatchPlanId> planIds) {
    List<BatchPlan> plans = planPort.findAllById(planIds);  // 1 query
    plans.forEach(plan -> plan.updateStatus(PlanStatus.COMPLETED));
    planPort.saveAll(plans);  // 1 batch update
}
```

---

### 3. Use Read-Only Transactions for Queries

```java
@Transactional(readOnly = true)
public List<BatchPlan> findActivePlans() {
    return planPort.findByStatus(PlanStatus.RUNNING);
}
```

**Benefits**:
- Optimization hint for database
- Prevents accidental writes
- May skip flush operations

---

### 4. Handle Optimistic Locking

```java
@Transactional
public void updatePlanWithRetry(UpdatePlanCommand command) {
    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
        try {
            BatchPlan plan = planPort.findById(command.planId());
            plan.updateStatus(command.status());
            planPort.save(plan);  // May throw OptimisticLockException
            return;

        } catch (OptimisticLockException e) {
            attempt++;
            if (attempt >= maxRetries) {
                throw new ApplicationException("Failed to update plan after " + maxRetries + " retries");
            }
            // Retry
        }
    }
}
```

---

## Summary

**Transaction Management**:
- ✅ `@Transactional` at orchestrator level
- ✅ Keep transactions short
- ❌ NEVER call external APIs inside transactions
- ✅ Use appropriate propagation (REQUIRED, REQUIRES_NEW)

**Error Handling**:
- ✅ Domain exceptions for business rules
- ✅ Application exceptions for orchestration failures
- ✅ Map to ProblemDetail in GlobalExceptionHandler
- ✅ Log errors with context

**Distributed Transactions**:
- ✅ Prefer Outbox Pattern (eventual consistency)
- ⚠️ Use Seata only when strong consistency required

**See Also**:
- [outbox-pattern.md](outbox-pattern.md) - Reliable event publishing
- [event-driven-architecture.md](event-driven-architecture.md) - Domain events
- [observability-guide.md](observability-guide.md) - Error logging and monitoring
