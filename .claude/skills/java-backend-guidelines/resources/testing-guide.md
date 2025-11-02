# Testing Guide (Unit, Integration, ArchUnit)

Complete testing reference for Hexagonal Architecture + DDD with Spring Boot. Teaches AI and developers how to identify code patterns and generate correct tests.

---

## Table of Contents

1. [Overview & Testing Pyramid](#overview--testing-pyramid)
2. [Testing Pattern Recognition](#testing-pattern-recognition) ⭐
3. [Domain Layer Testing](#domain-layer-testing)
4. [Application Layer Testing](#application-layer-testing)
   - [Orchestrator Testing](#orchestrator-testing)
   - [Coordinator Testing](#coordinator-testing) ⭐
   - [Event Handler Testing](#event-handler-testing) ⭐
5. [Infrastructure Layer Testing](#infrastructure-layer-testing)
   - [Repository Testing](#repository-testing)
   - [Converter Testing](#converter-testing) ⭐
6. [Adapter Layer Testing](#adapter-layer-testing)
   - [REST Controller Testing](#rest-controller-testing)
   - [XXL-Job Testing](#xxl-job-testing) ⭐
7. [Integration Testing Patterns](#integration-testing-patterns) ⭐
   - [Event Chain Testing](#event-chain-testing)
   - [Outbox Pattern Testing](#outbox-pattern-testing)
   - [Transaction Boundary Testing](#transaction-boundary-testing)
   - [Concurrency & Optimistic Locking Testing](#concurrency--optimistic-locking-testing)
8. [Test Data Management](#test-data-management) ⭐
9. [Test Coverage Strategy](#test-coverage-strategy) ⭐
10. [ArchUnit Tests](#archunit-tests)
11. [Testing Best Practices](#testing-best-practices)
12. [Testing Checklists & Quick Reference](#testing-checklists--quick-reference) ⭐

---

## Overview & Testing Pyramid

Papertrace follows the **Testing Pyramid** approach: many fast unit tests, fewer integration tests, and minimal end-to-end tests. Tests are organized by layer (Domain, Application, Infrastructure, Adapter) to match Hexagonal Architecture.

**Core Principle**: Test each layer in isolation with appropriate test doubles (mocks, stubs, test containers).

### Testing Pyramid

```
         /\
        /  \      E2E Tests (few, slow)
       /----\     - Full system scenarios
      /      \    - Critical user journeys
     /--------\   Integration Tests (moderate)
    /          \  - Database interactions
   /------------\ - External API calls
  /______________\ Unit Tests (many, fast)
                   - Domain logic
                   - Business rules
                   - Pure Java
```

**Target Distribution**:
- **70% Unit Tests**: Domain + Application layer logic
- **25% Integration Tests**: Infrastructure + Database
- **5% E2E Tests**: Critical workflows only

### Test Organization

```
patra-{service}/
├── patra-{service}-domain/
│   └── src/test/java/                    # Domain unit tests
│       └── com/patra/{service}/domain/
│           ├── model/
│           │   ├── ProvenanceTest.java
│           │   └── PlanAggregateTest.java
│           └── vo/
│               └── WindowSpecTest.java
│
├── patra-{service}-app/
│   └── src/test/java/                    # Application unit tests
│       └── com/patra/{service}/app/
│           ├── orchestrator/
│           │   └── PlanIngestionOrchestratorTest.java
│           ├── coordinator/
│           │   └── PlanPersistenceCoordinatorTest.java
│           └── eventhandler/
│               └── TaskCompletedEventHandlerTest.java
│
├── patra-{service}-infra/
│   └── src/test/java/                    # Infrastructure unit tests
│       └── com/patra/{service}/infra/
│           └── converter/
│               └── PlanConverterTest.java
│
├── patra-{service}-adapter/
│   └── src/test/java/                    # Adapter unit tests
│       └── com/patra/{service}/adapter/
│           ├── rest/
│           │   └── ProvenanceControllerTest.java
│           └── job/
│               └── PubmedHarvestJobTest.java
│
└── patra-{service}-boot/
    └── src/test/java/                    # Integration tests
        └── com/patra/{service}/
            ├── integration/              # Database + Event integration
            │   ├── EventChainIntegrationTest.java
            │   └── OutboxPatternIntegrationTest.java
            └── architecture/             # ArchUnit tests
                └── ArchitectureTest.java
```

---

## Testing Pattern Recognition

**Purpose**: Help AI identify code patterns and apply correct testing strategies.

### Code Pattern → Test Strategy Mapping

When you see specific code patterns, immediately know which testing approach to use:

| Code Feature | Context | Test Strategy | Key Verification Points | Reference |
|--------------|---------|---------------|-------------------------|-----------|
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | Event Handler | Integration test + Mock Publisher | Event publishing, idempotency check, optimistic lock handling | [§4.3](#event-handler-testing) |
| `Orchestrator + multiple Coordinators` | Use case coordination | Layered mocking | Call order (InOrder), exception wrapping | [§4.1](#orchestrator-testing), [§4.2](#coordinator-testing) |
| `OutboxMessage.builder()...save()` | Outbox Pattern | Integration test + TestContainers | Atomicity with business data, dedupKey uniqueness | [§7.2](#outbox-pattern-testing) |
| `@XxlJob` annotation | Scheduled job | Mock XxlJobHelper | Parameter parsing, job execution flow | [§6.2](#xxl-job-testing) |
| `@RestController` + `@Valid` | REST endpoint | @WebMvcTest + MockMvc | Validation failure responses, ProblemDetail | [§6.1](#rest-controller-testing) |
| `MapStruct` converter | DO ↔ Domain mapping | Unit test | Bidirectional mapping, null handling | [§5.2](#converter-testing) |
| `@Transactional` | Transaction boundary | Integration test | Rollback scenarios, REQUIRES_NEW propagation | [§7.3](#transaction-boundary-testing) |
| Aggregate with `version` field | Optimistic locking | Integration test | Concurrent update conflicts | [§7.4](#concurrency--optimistic-locking-testing) |

### Decision Tree: Choose Test Type

```
Q1: Does code have Spring dependencies (@Service, @Transactional, @Component)?
├─ NO → Pure unit test (Domain Layer)
│   - No mocks needed
│   - Fast execution (<100ms)
│   - Test business rules only
│
└─ YES → Q2: Does it need a database?
    ├─ NO → Spring mock test (Application Layer)
    │   - Mock all Ports
    │   - Use @ExtendWith(MockitoExtension.class)
    │   - Verify orchestration logic
    │
    └─ YES → Integration test (Integration Layer)
        - Use @SpringBootTest
        - Use TestContainers for real database
        - Test full workflow

Q3: Does code publish or handle Domain Events?
├─ NO → Standard testing as above
│
└─ YES → Event-driven testing required
    - Verify event publishing (Mock ApplicationEventPublisher)
    - Test idempotency (dedupKey check against Outbox)
    - Test optimistic lock conflict handling
    - Test AFTER_COMMIT behavior

Q4: Does code use Outbox Pattern?
├─ NO → Standard testing
│
└─ YES → Outbox testing required
    - Verify business data + outbox message atomicity
    - Test dedupKey uniqueness constraint
    - Test relay mechanism (lease, publish, retry)
```

### Decision Tree: Test Scope

Identify these key features in code to determine what to test:

```
Code has @Transactional?
├─ YES → Must test transaction rollback scenarios
└─ NO  → Skip transaction tests

Code has @TransactionalEventListener?
├─ YES → Must test:
│        - AFTER_COMMIT vs BEFORE_COMMIT behavior
│        - Event publishing verification
│        - Idempotency (dedupKey check)
│        - OptimisticLockingFailureException handling
└─ NO  → Skip event tests

Code creates OutboxMessage?
├─ YES → Must test atomicity with business table
└─ NO  → Skip outbox tests

Code has optimistic lock field (version)?
├─ YES → Must test concurrent update conflicts
└─ NO  → Skip concurrency tests

Code has @Valid parameter validation?
├─ YES → Must test validation failure responses
└─ NO  → Skip validation tests

Code calls multiple Coordinators?
├─ YES → Must verify call order with InOrder
└─ NO  → Skip order verification
```

---

## Domain Layer Testing

**Purpose**: Test **business logic** and **business rules** in isolation. No Spring, no database, no mocks needed.

### Characteristics

- ✅ Pure Java tests
- ✅ Fast execution (<100ms per test)
- ✅ No external dependencies
- ✅ Test domain invariants and business rules
- ✅ No mocks required (pure functions)

### Example 1: Testing a Value Object (Record)

```java
package com.patra.registry.domain.model.vo.provenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

class ProvenanceTest {

    @Test
    @DisplayName("Should create provenance with valid fields")
    void shouldCreateProvenanceWithValidFields() {
        // Arrange & Act
        Provenance provenance = new Provenance(
            1L,
            "PUBMED",
            "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK3827/",
            true,
            "ACTIVE"
        );

        // Assert
        assertThat(provenance.id()).isEqualTo(1L);
        assertThat(provenance.code()).isEqualTo("PUBMED");
        assertThat(provenance.name()).isEqualTo("PubMed");
        assertThat(provenance.baseUrlDefault()).isEqualTo("https://pubmed.ncbi.nlm.nih.gov");
        assertThat(provenance.active()).isTrue();
        assertThat(provenance.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should reject null code")
    void shouldRejectNullCode() {
        // Act & Assert
        assertThatThrownBy(() -> new Provenance(
            1L, null, "Name", "https://url.com", "UTC", "https://docs.com", true, "ACTIVE"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code cannot be null or blank");
    }

    @Test
    @DisplayName("Should trim whitespace from optional fields")
    void shouldTrimWhitespaceFromOptionalFields() {
        // Arrange & Act
        Provenance provenance = new Provenance(
            1L,
            "PUBMED",
            "PubMed",
            "  https://pubmed.ncbi.nlm.nih.gov  ",  // with spaces
            "UTC",
            "  https://docs.com  ",  // with spaces
            true,
            "ACTIVE"
        );

        // Assert: whitespace trimmed
        assertThat(provenance.baseUrlDefault()).isEqualTo("https://pubmed.ncbi.nlm.nih.gov");
        assertThat(provenance.docsUrl()).isEqualTo("https://docs.com");
    }
}
```

### Example 2: Testing Sealed Interface (WindowSpec)

```java
package com.patra.ingest.domain.model.vo.plan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class WindowSpecTest {

    @Test
    @DisplayName("Should create TIME window with valid timestamps")
    void shouldCreateTimeWindow() {
        // Arrange
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-01-31T23:59:59Z");

        // Act
        WindowSpec.Time window = WindowSpec.ofTime(from, to);

        // Assert
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
        assertThat(window.strategy().getCode()).isEqualTo("TIME");
    }

    @Test
    @DisplayName("Should reject TIME window where from is after to")
    void shouldRejectInvalidTimeWindow() {
        // Arrange
        Instant from = Instant.parse("2024-12-31T00:00:00Z");
        Instant to = Instant.parse("2024-01-01T00:00:00Z");

        // Act & Assert
        assertThatThrownBy(() -> WindowSpec.ofTime(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("from must be before or equal to to");
    }

    @Test
    @DisplayName("Should convert TIME window to map")
    void shouldConvertTimeWindowToMap() {
        // Arrange
        WindowSpec.Time window = WindowSpec.ofTime(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-31T23:59:59Z")
        );

        // Act
        var map = window.toMap();

        // Assert
        assertThat(map).containsKey("strategy");
        assertThat(map).containsKey("window");
        assertThat(map.get("strategy")).isEqualTo("TIME");
    }
}
```

### Example 3: Testing Aggregate Root

```java
package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class PlanAggregateTest {

    @Test
    @DisplayName("Should create plan aggregate with DRAFT status")
    void shouldCreatePlanWithDraftStatus() {
        // Arrange
        WindowSpec windowSpec = WindowSpec.ofTime(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-31T23:59:59Z")
        );

        // Act
        PlanAggregate plan = PlanAggregate.create(
            100L,                              // scheduleInstanceId
            "PUBMED:HARVEST:1704067200000",    // planKey
            "PUBMED",                          // provenanceCode
            "HARVEST",                         // operationCode
            "expr-hash-123",                   // exprProtoHash
            "{\"fields\":[]}",                 // exprProtoSnapshotJson
            "{\"windowOffset\":{}}",           // provenanceConfigSnapshotJson
            "config-hash-456",                 // provenanceConfigHash
            windowSpec,                        // windowSpec
            "TIME",                            // sliceStrategyCode
            "{}"                               // sliceParamsJson
        );

        // Assert
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getProvenanceCode()).isEqualTo("PUBMED");
        assertThat(plan.getPlanKey()).isEqualTo("PUBMED:HARVEST:1704067200000");
    }

    @Test
    @DisplayName("Should transition from DRAFT to SLICING")
    void shouldTransitionFromDraftToSlicing() {
        // Arrange
        PlanAggregate plan = createTestPlan();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);

        // Act
        plan.startSlicing();

        // Assert
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.SLICING);
    }

    @Test
    @DisplayName("Should mark plan as READY")
    void shouldMarkPlanAsReady() {
        // Arrange
        PlanAggregate plan = createTestPlan();
        plan.startSlicing();

        // Act
        plan.markReady();

        // Assert
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.READY);
    }

    private PlanAggregate createTestPlan() {
        return PlanAggregate.create(
            100L,
            "TEST:HARVEST:12345",
            "PUBMED",
            "HARVEST",
            "hash123",
            "{}",
            "{}",
            "confighash",
            WindowSpec.ofSingle(),
            "SINGLE",
            "{}"
        );
    }
}
```

### Example 4: Testing Domain Service

```java
package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SliceStatusCalculatorTest {

    private final SliceStatusCalculator calculator = new SliceStatusCalculator();

    @Test
    @DisplayName("Should calculate COMPLETED when all tasks completed")
    void shouldCalculateCompletedWhenAllTasksCompleted() {
        // Arrange: All tasks are COMPLETED
        List<TaskStatus> taskStatuses = List.of(
            TaskStatus.COMPLETED,
            TaskStatus.COMPLETED,
            TaskStatus.COMPLETED
        );

        // Act
        SliceStatus result = calculator.calculate(taskStatuses);

        // Assert
        assertThat(result).isEqualTo(SliceStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should calculate FAILED when any task failed")
    void shouldCalculateFailedWhenAnyTaskFailed() {
        // Arrange: One task FAILED
        List<TaskStatus> taskStatuses = List.of(
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.IN_PROGRESS
        );

        // Act
        SliceStatus result = calculator.calculate(taskStatuses);

        // Assert
        assertThat(result).isEqualTo(SliceStatus.FAILED);
    }

    @Test
    @DisplayName("Should calculate IN_PROGRESS when some tasks running")
    void shouldCalculateInProgressWhenSomeTasksRunning() {
        // Arrange
        List<TaskStatus> taskStatuses = List.of(
            TaskStatus.COMPLETED,
            TaskStatus.IN_PROGRESS,
            TaskStatus.PENDING
        );

        // Act
        SliceStatus result = calculator.calculate(taskStatuses);

        // Assert
        assertThat(result).isEqualTo(SliceStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Should handle empty task list")
    void shouldHandleEmptyTaskList() {
        // Act & Assert
        assertThatThrownBy(() -> calculator.calculate(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task list cannot be empty");
    }
}
```

---

## Application Layer Testing

**Purpose**: Test **orchestration logic** without database or external dependencies.

### Characteristics

- ✅ Mock domain ports (interfaces)
- ✅ Test coordination flow
- ✅ Verify port interactions
- ❌ No database (use mocks)

### Dependencies

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

### Orchestrator Testing

Test **use case coordination** by mocking all coordinators and ports.

#### Example: Testing Orchestrator

```java
package com.patra.ingest.app.orchestrator.plan;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PatraRegistryPort;
import com.patra.ingest.app.coordinator.PlanPersistenceCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {

    @Mock
    private PatraRegistryPort registryPort;

    @Mock
    private PlanPersistenceCoordinator persistenceCoordinator;

    @Mock
    private EventPublishingCoordinator eventCoordinator;

    @InjectMocks
    private PlanIngestionOrchestrator orchestrator;

    @Test
    @DisplayName("Should orchestrate plan ingestion successfully")
    void shouldOrchestrateSuccessfully() {
        // Arrange
        String provenanceCode = "PUBMED";
        String operationCode = "HARVEST";
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-01-31T23:59:59Z");

        // Mock registry responses
        when(registryPort.fetchConfigSnapshot(provenanceCode, operationCode))
            .thenReturn("{\"windowOffset\":{\"mode\":\"SLIDING\"}}");
        when(registryPort.fetchExprSnapshot(provenanceCode, operationCode, from))
            .thenReturn("{\"fields\":[{\"key\":\"publication_date\"}]}");

        // Act
        PlanIngestionContext context = new PlanIngestionContext(
            provenanceCode,
            operationCode,
            from,
            to
        );
        orchestrator.ingestPlan(context);

        // Assert: Verify coordinator call order
        InOrder inOrder = inOrder(persistenceCoordinator, eventCoordinator);
        inOrder.verify(persistenceCoordinator).persistPlan(any(PlanAggregate.class));
        inOrder.verify(eventCoordinator).publishPlanCreatedEvent(any(PlanAggregate.class));
    }

    @Test
    @DisplayName("Should rollback when persistence fails")
    void shouldRollbackOnPersistenceFailure() {
        // Arrange
        when(registryPort.fetchConfigSnapshot(anyString(), anyString()))
            .thenReturn("{}");
        when(registryPort.fetchExprSnapshot(anyString(), anyString(), any()))
            .thenReturn("{}");

        // Simulate persistence failure
        doThrow(new RuntimeException("DB error"))
            .when(persistenceCoordinator).persistPlan(any());

        // Act & Assert
        PlanIngestionContext context = new PlanIngestionContext(
            "PUBMED", "HARVEST", Instant.now(), Instant.now()
        );

        assertThatThrownBy(() -> orchestrator.ingestPlan(context))
            .isInstanceOf(RuntimeException.class);

        // Verify event coordinator never called (transaction rollback)
        verify(eventCoordinator, never()).publishPlanCreatedEvent(any());
    }
}
```

---

### Coordinator Testing

Test **concern-specific coordination** in isolation from orchestrators.

#### Purpose

Coordinators handle single concerns (persistence, event publishing, validation). Test them independently with mocked dependencies.

#### Example: Testing Persistence Coordinator

```java
package com.patra.ingest.app.coordinator;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.SliceAggregate;
import com.patra.ingest.domain.port.PlanRepositoryPort;
import com.patra.ingest.domain.port.SliceRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanPersistenceCoordinatorTest {

    @Mock
    private PlanRepositoryPort planRepo;

    @Mock
    private SliceRepositoryPort sliceRepo;

    @InjectMocks
    private PlanPersistenceCoordinator coordinator;

    @Test
    @DisplayName("Should persist plan with all slices atomically")
    void shouldPersistPlanWithSlices() {
        // Arrange: Prepare Plan + Slices
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan()
            .withId(1L)
            .build();

        List<SliceAggregate> slices = List.of(
            SliceTestBuilder.aSlice().withId(1L).withPlanId(1L).build(),
            SliceTestBuilder.aSlice().withId(2L).withPlanId(1L).build()
        );

        when(planRepo.save(any())).thenReturn(plan);

        // Act
        coordinator.persistPlanWithSlices(plan, slices);

        // Assert: Verify call order (plan first, then slices)
        InOrder inOrder = inOrder(planRepo, sliceRepo);
        inOrder.verify(planRepo).save(plan);
        inOrder.verify(sliceRepo).batchSave(slices);
    }

    @Test
    @DisplayName("Should wrap infrastructure exceptions")
    void shouldWrapInfrastructureExceptions() {
        // Arrange
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().build();
        when(planRepo.save(any()))
            .thenThrow(new DataAccessException("DB connection lost") {});

        // Act & Assert: Verify exception wrapping
        assertThatThrownBy(() -> coordinator.persistPlanWithSlices(plan, List.of()))
            .isInstanceOf(PlanPersistenceException.class)
            .hasMessageContaining("Failed to persist plan")
            .hasCauseInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Should skip slice persistence when list is empty")
    void shouldSkipSlicePersistenceWhenEmpty() {
        // Arrange
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().build();
        when(planRepo.save(any())).thenReturn(plan);

        // Act
        coordinator.persistPlanWithSlices(plan, List.of());

        // Assert: sliceRepo never called
        verify(planRepo).save(plan);
        verifyNoInteractions(sliceRepo);
    }
}
```

**Key Testing Points for Coordinators**:
- Mock all dependencies (ports, other coordinators)
- Verify call order with `InOrder`
- Test exception wrapping (infrastructure → domain exceptions)
- Test edge cases (empty lists, null handling)

---

### Event Handler Testing

Test **domain event handlers** with Spring context and event publishing verification.

#### Purpose

Event handlers (@TransactionalEventListener) react to domain events. They require special testing considerations:
- AFTER_COMMIT phase verification
- Idempotency checks
- Optimistic lock conflict handling
- Event chain propagation

#### Example 1: Testing Event Publishing and Idempotency

```java
package com.patra.ingest.app.eventhandler;

import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.model.aggregate.SliceAggregate;
import com.patra.ingest.domain.port.SliceRepositoryPort;
import com.patra.ingest.infra.outbox.OutboxMessage;
import com.patra.ingest.infra.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class TaskCompletedEventHandlerTest {

    @Autowired
    private TaskCompletedEventHandler eventHandler;

    @Autowired
    private SliceRepositoryPort sliceRepo;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Should update slice status and publish event AFTER_COMMIT")
    void shouldUpdateSliceAndPublishEvent() {
        // Arrange: Prepare completed task event
        Long taskId = 1L;
        Long sliceId = 100L;
        String dedupKey = "task-completed-1";

        // Prepare existing Slice
        SliceAggregate slice = SliceTestBuilder.aSlice()
            .withId(sliceId)
            .withStatus(SliceStatus.IN_PROGRESS)
            .build();
        sliceRepo.save(slice);

        TaskCompletedEvent event = new TaskCompletedEvent(
            taskId, sliceId, TaskStatus.COMPLETED, dedupKey, Instant.now()
        );

        // Act: Handle event
        eventHandler.onTaskCompleted(event);

        // Assert 1: Verify Slice status updated
        SliceAggregate updated = sliceRepo.findById(sliceId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SliceStatus.COMPLETED);

        // Assert 2: Verify event published (AFTER_COMMIT phase)
        verify(eventPublisher).publishEvent(argThat(e ->
            e instanceof SliceStatusChangedEvent &&
            ((SliceStatusChangedEvent) e).sliceId().equals(sliceId)
        ));

        // Assert 3: Verify idempotency record (Outbox table)
        OutboxMessage outbox = outboxRepo.findByDedupKey(dedupKey).orElseThrow();
        assertThat(outbox.getEventType()).isEqualTo("SliceStatusChangedEvent");
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Should NOT process duplicate event (idempotency)")
    void shouldNotProcessDuplicateEvent() {
        // Arrange: Prepare duplicate event (dedupKey already exists)
        String dedupKey = "task-completed-duplicate";
        outboxRepo.save(OutboxMessage.builder()
            .dedupKey(dedupKey)
            .eventType("SliceStatusChangedEvent")
            .status("PUBLISHED")
            .build());

        TaskCompletedEvent event = new TaskCompletedEvent(
            1L, 100L, TaskStatus.COMPLETED, dedupKey, Instant.now()
        );

        // Act
        eventHandler.onTaskCompleted(event);

        // Assert: Verify business logic NOT executed (idempotency protection)
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(sliceRepo);
    }

    @Test
    @DisplayName("Should handle OptimisticLockingFailureException gracefully")
    void shouldHandleOptimisticLockConflict() {
        // Arrange: Simulate concurrent update causing optimistic lock conflict
        Long sliceId = 100L;
        String dedupKey = "task-completed-conflict";

        SliceAggregate slice = SliceTestBuilder.aSlice()
            .withId(sliceId)
            .withVersion(1L)
            .build();
        sliceRepo.save(slice);

        TaskCompletedEvent event = new TaskCompletedEvent(
            1L, sliceId, TaskStatus.COMPLETED, dedupKey, Instant.now()
        );

        // Simulate version conflict (another transaction updated the slice)
        doThrow(new OptimisticLockingFailureException("Version conflict"))
            .when(sliceRepo).save(any());

        // Act & Assert: Verify exception handling
        assertThatThrownBy(() -> eventHandler.onTaskCompleted(event))
            .isInstanceOf(OptimisticLockingFailureException.class);

        // Verify no subsequent event published (transaction rolled back)
        verify(eventPublisher, never()).publishEvent(any());
    }
}
```

#### Example 2: Testing Event Chain Propagation

```java
@SpringBootTest
@Transactional
class SliceStatusChangedEventHandlerTest {

    @Autowired
    private SliceStatusChangedEventHandler eventHandler;

    @Autowired
    private PlanRepositoryPort planRepo;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("Should propagate slice status to plan aggregate")
    void shouldPropagateSliceStatusToPlan() {
        // Arrange: Prepare Plan with multiple Slices
        Long planId = 1L;
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan()
            .withId(planId)
            .withStatus(PlanStatus.IN_PROGRESS)
            .build();
        planRepo.save(plan);

        // All slices completed
        SliceStatusChangedEvent event = new SliceStatusChangedEvent(
            100L, planId, SliceStatus.COMPLETED, "dedupKey-100", Instant.now()
        );

        // Act
        eventHandler.onSliceStatusChanged(event);

        // Assert: Plan status updated to COMPLETED (if all slices completed)
        PlanAggregate updated = planRepo.findById(planId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PlanStatus.COMPLETED);

        // Verify PlanStatusChangedEvent published
        verify(eventPublisher).publishEvent(argThat(e ->
            e instanceof PlanStatusChangedEvent &&
            ((PlanStatusChangedEvent) e).planId().equals(planId)
        ));
    }
}
```

**Key Testing Points for Event Handlers**:
- Use `@SpringBootTest` (needs Spring context for event publishing)
- Mock `ApplicationEventPublisher` to verify event publishing
- Test idempotency: duplicate events should be ignored
- Test optimistic lock conflicts
- Test event chain propagation (Task → Slice → Plan)
- Verify AFTER_COMMIT behavior (events published after transaction commits)

---

## Infrastructure Layer Testing

**Purpose**: Test **database interactions** and **repository implementations** with a real database.

### Characteristics

- ✅ Real database (MySQL in Docker)
- ✅ Test SQL queries and mappings
- ✅ Verify MyBatis-Plus and MapStruct
- ⚠️ Slower than unit tests (seconds)

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```

---

### Repository Testing

Test repository implementations with TestContainers (real MySQL database).

#### Example: Repository Integration Test

```java
package com.patra.registry.integration;

import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.infra.persistence.repository.ProvenanceRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ProvenanceRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("patra_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private ProvenanceRepositoryImpl repository;

    @Test
    void shouldSaveAndFindProvenance() {
        // Arrange
        Provenance provenance = new Provenance(
            null,  // id will be auto-generated
            "PUBMED",
            "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK3827/",
            true,
            "ACTIVE"
        );

        // Act
        Provenance saved = repository.save(provenance);
        Optional<Provenance> found = repository.findByCode("PUBMED");

        // Assert
        assertThat(saved.id()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().code()).isEqualTo("PUBMED");
        assertThat(found.get().name()).isEqualTo("PubMed");
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void shouldFindAllActiveProvenances() {
        // Arrange
        repository.save(createProvenance("PUBMED", true));
        repository.save(createProvenance("EPMC", true));
        repository.save(createProvenance("CROSSREF", false));

        // Act
        var activeProvenances = repository.findAllActive();

        // Assert
        assertThat(activeProvenances).hasSize(2);
        assertThat(activeProvenances)
            .extracting(Provenance::code)
            .containsExactlyInAnyOrder("PUBMED", "EPMC");
    }

    private Provenance createProvenance(String code, boolean active) {
        return new Provenance(
            null,
            code,
            code + " Name",
            "https://" + code.toLowerCase() + ".com",
            "UTC",
            "https://docs.com",
            active,
            active ? "ACTIVE" : "INACTIVE"
        );
    }
}
```

#### Base Test Class for TestContainers

```java
package com.patra.registry.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("patra_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);  // Reuse container across tests

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

---

### Converter Testing

Test **MapStruct converters** for Domain ↔ DO (Data Object) mapping.

#### Purpose

Converters translate between domain models and database entities. Test bidirectional mapping correctness, null handling, and complex mappings.

#### Example: Testing MapStruct Converter

```java
package com.patra.registry.infra.converter;

import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.infra.persistence.dataobject.ProvenanceDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

class ProvenanceConverterTest {

    private final ProvenanceConverter converter = ProvenanceConverter.INSTANCE;

    @Test
    @DisplayName("Should convert Domain → DO correctly")
    void shouldConvertDomainToDO() {
        // Arrange
        Provenance domain = new Provenance(
            1L, "PUBMED", "PubMed", "https://pubmed.gov",
            "UTC", "https://docs.com", true, "ACTIVE"
        );

        // Act
        ProvenanceDO dataObject = converter.toDataObject(domain);

        // Assert: Verify all field mappings
        assertThat(dataObject.getId()).isEqualTo(1L);
        assertThat(dataObject.getCode()).isEqualTo("PUBMED");
        assertThat(dataObject.getName()).isEqualTo("PubMed");
        assertThat(dataObject.getBaseUrl()).isEqualTo("https://pubmed.gov");
        assertThat(dataObject.getActive()).isTrue();
    }

    @Test
    @DisplayName("Should handle null fields gracefully")
    void shouldHandleNullFields() {
        // Arrange: Optional fields are null
        Provenance domain = new Provenance(
            1L, "PUBMED", "PubMed", "https://pubmed.gov",
            null, null, true, "ACTIVE"  // timezone, docUrl are null
        );

        // Act
        ProvenanceDO dataObject = converter.toDataObject(domain);

        // Assert: null fields correctly converted
        assertThat(dataObject.getTimezone()).isNull();
        assertThat(dataObject.getDocUrl()).isNull();
    }

    @Test
    @DisplayName("Should convert DO → Domain (round-trip)")
    void shouldConvertDOToDomain() {
        // Arrange
        ProvenanceDO dataObject = new ProvenanceDO();
        dataObject.setId(1L);
        dataObject.setCode("PUBMED");
        dataObject.setName("PubMed");
        dataObject.setBaseUrl("https://pubmed.gov");
        dataObject.setActive(true);

        // Act
        Provenance domain = converter.toDomain(dataObject);

        // Assert: Bidirectional mapping consistency
        assertThat(domain.id()).isEqualTo(1L);
        assertThat(domain.code()).isEqualTo("PUBMED");
        assertThat(domain.name()).isEqualTo("PubMed");
        assertThat(domain.active()).isTrue();
    }

    @Test
    @DisplayName("Should convert list of DOs to Domains")
    void shouldConvertListOfDOs() {
        // Arrange
        List<ProvenanceDO> dataObjects = List.of(
            createDO("PUBMED", "PubMed"),
            createDO("EPMC", "Europe PMC")
        );

        // Act
        List<Provenance> domains = converter.toDomainList(dataObjects);

        // Assert
        assertThat(domains).hasSize(2);
        assertThat(domains)
            .extracting(Provenance::code)
            .containsExactly("PUBMED", "EPMC");
    }

    private ProvenanceDO createDO(String code, String name) {
        ProvenanceDO dataObject = new ProvenanceDO();
        dataObject.setCode(code);
        dataObject.setName(name);
        dataObject.setActive(true);
        return dataObject;
    }
}
```

**Key Testing Points for Converters**:
- Test bidirectional mapping (Domain → DO → Domain)
- Test null handling (optional fields)
- Test list conversions
- Test complex nested objects
- Verify field name mappings (especially when names differ)

---

## Adapter Layer Testing

**Purpose**: Test **adapters** (REST controllers, scheduled jobs) without full application context.

### Characteristics

- ✅ Test HTTP endpoints or job execution
- ✅ Validate request/response
- ✅ Mock application layer
- ❌ No database

---

### REST Controller Testing

Test REST endpoints with MockMvc (without starting full app).

#### Example: Controller Test

```java
package com.patra.registry.adapter.rest;

import com.patra.registry.app.service.ProvenanceService;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProvenanceController.class)
class ProvenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProvenanceService provenanceService;

    @Test
    @DisplayName("Should get provenance by code")
    void shouldGetProvenanceByCode() throws Exception {
        // Arrange
        Provenance provenance = new Provenance(
            1L, "PUBMED", "PubMed",
            "https://pubmed.ncbi.nlm.nih.gov",
            "UTC", "https://docs.com", true, "ACTIVE"
        );
        when(provenanceService.findByCode("PUBMED")).thenReturn(provenance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/provenances/PUBMED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("PUBMED"))
            .andExpect(jsonPath("$.name").value("PubMed"))
            .andExpect(jsonPath("$.active").value(true));

        verify(provenanceService).findByCode("PUBMED");
    }

    @Test
    @DisplayName("Should return 404 when provenance not found")
    void shouldReturnNotFoundWhenProvenanceDoesNotExist() throws Exception {
        // Arrange
        when(provenanceService.findByCode("UNKNOWN"))
            .thenThrow(new ProvenanceNotFoundException("UNKNOWN"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/provenances/UNKNOWN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Provenance Not Found"));
    }

    @Test
    @DisplayName("Should validate request body with @Valid")
    void shouldValidateRequestBody() throws Exception {
        // Arrange: Invalid JSON (code is null)
        String invalidJson = """
            {
                "name": "Test Provenance",
                "baseUrlDefault": "https://test.com"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/provenances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.violations[0].field").value("code"))
            .andExpect(jsonPath("$.violations[0].message").value("must not be null"));
    }
}
```

**Key Testing Points for REST Controllers**:
- Use `@WebMvcTest` for controller slice testing
- Mock application layer services
- Test validation with `@Valid`
- Verify ProblemDetail error responses
- Test different HTTP status codes (200, 404, 400, 500)

---

### XXL-Job Testing

Test **scheduled jobs** with mocked XxlJobHelper.

#### Purpose

XXL-Job methods require special testing: mock XxlJobHelper for parameter parsing and job execution flow.

#### Example: Testing XXL-Job

```java
package com.patra.ingest.adapter.job;

import com.patra.ingest.app.orchestrator.PlanIngestionOrchestrator;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.xxl.job.core.biz.model.ReturnT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubmedHarvestJobTest {

    @Mock
    private PlanIngestionOrchestrator orchestrator;

    @InjectMocks
    private PubmedHarvestJob job;

    @Test
    @DisplayName("Should execute job with correct parameters")
    void shouldExecuteJobWithParameters() throws Exception {
        // Arrange: Mock XxlJobHelper
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getJobParam).thenReturn("provenanceCode=PUBMED");

            // Act
            ReturnT<String> result = job.harvestPubmedData("provenanceCode=PUBMED");

            // Assert
            assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
            assertThat(result.getMsg()).contains("Success");

            // Verify orchestrator called with correct parameter
            verify(orchestrator).ingestFromProvenance("PUBMED");

            // Verify job log
            helper.verify(() -> XxlJobHelper.log("Starting PUBMED harvest..."));
        }
    }

    @Test
    @DisplayName("Should handle job execution failure")
    void shouldHandleJobFailure() throws Exception {
        // Arrange: Simulate job failure
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            helper.when(XxlJobHelper::getJobParam).thenReturn("provenanceCode=PUBMED");

            doThrow(new RuntimeException("Ingestion failed"))
                .when(orchestrator).ingestFromProvenance(any());

            // Act
            ReturnT<String> result = job.harvestPubmedData("provenanceCode=PUBMED");

            // Assert: Verify failure response
            assertThat(result.getCode()).isEqualTo(ReturnT.FAIL_CODE);
            assertThat(result.getMsg()).contains("Ingestion failed");

            // Verify error logged
            helper.verify(() -> XxlJobHelper.log(contains("Job failed")));
        }
    }

    @Test
    @DisplayName("Should parse complex job parameters")
    void shouldParseComplexParameters() throws Exception {
        // Arrange
        try (MockedStatic<XxlJobHelper> helper = mockStatic(XxlJobHelper.class)) {
            String params = "provenanceCode=PUBMED&dateFrom=2024-01-01&dateTo=2024-01-31";
            helper.when(XxlJobHelper::getJobParam).thenReturn(params);

            // Act
            ReturnT<String> result = job.harvestPubmedDataWithDateRange(params);

            // Assert
            assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);

            // Verify orchestrator called with parsed parameters
            verify(orchestrator).ingestFromProvenanceWithDateRange(
                eq("PUBMED"),
                eq("2024-01-01"),
                eq("2024-01-31")
            );
        }
    }
}
```

**Key Testing Points for XXL-Job**:
- Mock `XxlJobHelper` with `MockedStatic`
- Test parameter parsing (`getJobParam()`)
- Test job execution flow
- Test failure scenarios and error handling
- Verify job logging (`XxlJobHelper.log()`)

---

## Integration Testing Patterns

**Purpose**: Test **end-to-end workflows** with real database and event propagation.

### Characteristics

- ✅ Real database (TestContainers)
- ✅ Real event publishing
- ✅ Test complete workflows
- ⚠️ Slowest tests (multiple seconds)

---

### Event Chain Testing

Test **end-to-end event propagation** through multiple aggregates.

#### Purpose

Verify that domain events propagate correctly through the system (Task → Slice → Plan).

#### Example: Testing Event Chain

```java
package com.patra.ingest.integration;

import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.SliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.PlanRepositoryPort;
import com.patra.ingest.domain.port.SliceRepositoryPort;
import com.patra.ingest.domain.port.TaskRepositoryPort;
import com.patra.ingest.infra.outbox.OutboxMessage;
import com.patra.ingest.infra.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class TaskToSliceEventChainIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("patra_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TaskRepositoryPort taskRepo;

    @Autowired
    private SliceRepositoryPort sliceRepo;

    @Autowired
    private PlanRepositoryPort planRepo;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @Test
    @DisplayName("Should propagate Task → Slice → Plan status updates")
    void shouldPropagateStatusUpdates() {
        // Arrange: Prepare Plan → Slice → Task hierarchy
        PlanAggregate plan = planRepo.save(PlanTestBuilder.aDefaultPlan()
            .withStatus(PlanStatus.IN_PROGRESS)
            .build());

        SliceAggregate slice = sliceRepo.save(SliceTestBuilder.aSlice()
            .withPlanId(plan.getId())
            .withStatus(SliceStatus.IN_PROGRESS)
            .build());

        TaskAggregate task = taskRepo.save(TaskTestBuilder.aTask()
            .withSliceId(slice.getId())
            .withStatus(TaskStatus.IN_PROGRESS)
            .build());

        // Act: Publish TaskCompletedEvent (simulate task completion)
        eventPublisher.publishEvent(new TaskCompletedEvent(
            task.getId(),
            slice.getId(),
            TaskStatus.COMPLETED,
            "dedupKey-" + task.getId(),
            Instant.now()
        ));

        // Wait for event chain to complete (AFTER_COMMIT)
        flushAndClear();

        // Assert 1: Slice status updated
        SliceAggregate updatedSlice = sliceRepo.findById(slice.getId()).orElseThrow();
        assertThat(updatedSlice.getStatus()).isEqualTo(SliceStatus.COMPLETED);

        // Assert 2: Plan status updated (assuming all slices completed)
        PlanAggregate updatedPlan = planRepo.findById(plan.getId()).orElseThrow();
        assertThat(updatedPlan.getStatus()).isEqualTo(PlanStatus.COMPLETED);

        // Assert 3: Verify Outbox event records
        List<OutboxMessage> outboxMessages = outboxRepo.findAll();
        assertThat(outboxMessages)
            .extracting(OutboxMessage::getEventType)
            .contains("SliceStatusChangedEvent", "PlanStatusChangedEvent");
    }

    @Test
    @DisplayName("Should handle concurrent task completions")
    void shouldHandleConcurrentCompletions() {
        // Arrange: Prepare multiple tasks for one slice
        PlanAggregate plan = planRepo.save(PlanTestBuilder.aDefaultPlan().build());
        SliceAggregate slice = sliceRepo.save(SliceTestBuilder.aSlice()
            .withPlanId(plan.getId()).build());

        List<TaskAggregate> tasks = IntStream.range(0, 5)
            .mapToObj(i -> taskRepo.save(TaskTestBuilder.aTask()
                .withSliceId(slice.getId())
                .withStatus(TaskStatus.IN_PROGRESS)
                .build()))
            .toList();

        // Act: Publish multiple task completion events concurrently
        tasks.parallelStream().forEach(task ->
            eventPublisher.publishEvent(new TaskCompletedEvent(
                task.getId(), slice.getId(), TaskStatus.COMPLETED,
                "dedupKey-" + task.getId(), Instant.now()
            ))
        );

        // Assert: Verify idempotency (no duplicate event processing)
        long outboxCount = outboxRepo.count();
        assertThat(outboxCount).isLessThanOrEqualTo(tasks.size() + 1); // Task events + 1 Slice event
    }

    private void flushAndClear() {
        // Helper method to flush and clear entity manager (for testing)
        // Implementation depends on your setup
    }
}
```

**Key Testing Points for Event Chains**:
- Test end-to-end propagation (Task → Slice → Plan)
- Verify all levels of aggregate updates
- Test concurrent event handling
- Verify idempotency across the chain
- Check Outbox message creation at each level

---

### Outbox Pattern Testing

Test **transactional outbox** for reliable event publishing.

#### Purpose

Verify that business data and outbox messages are saved atomically, and relay mechanism works correctly.

#### Example: Testing Outbox Atomicity

```java
package com.patra.ingest.integration;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepositoryPort;
import com.patra.ingest.infra.outbox.OutboxMessage;
import com.patra.ingest.infra.outbox.OutboxMessageRepository;
import com.patra.ingest.app.coordinator.PlanPersistenceCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class OutboxPatternIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("patra_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private PlanRepositoryPort planRepo;

    @Autowired
    private OutboxMessageRepository outboxRepo;

    @Autowired
    private PlanPersistenceCoordinator coordinator;

    @Test
    @DisplayName("Should persist business data and outbox message atomically")
    void shouldPersistAtomically() {
        // Arrange
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().build();

        // Act: Persist Plan + create Outbox message
        coordinator.persistPlanWithOutbox(plan);

        // Assert 1: Plan saved
        PlanAggregate saved = planRepo.findById(plan.getId()).orElseThrow();
        assertThat(saved).isNotNull();

        // Assert 2: Outbox message created (same transaction)
        OutboxMessage outbox = outboxRepo.findByDedupKey("plan-created-" + plan.getId()).orElseThrow();
        assertThat(outbox.getEventType()).isEqualTo("PlanCreatedEvent");
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getPayload()).contains(plan.getId().toString());
    }

    @Test
    @DisplayName("Should rollback outbox message when business operation fails")
    void shouldRollbackOutboxOnFailure() {
        // Arrange: Mock business logic failure
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan()
            .withInvalidData()  // Trigger validation failure
            .build();

        // Act & Assert
        assertThatThrownBy(() -> coordinator.persistPlanWithOutbox(plan))
            .isInstanceOf(ValidationException.class);

        // Verify: Both Plan and Outbox message NOT saved (transaction rollback)
        assertThat(planRepo.findById(plan.getId())).isEmpty();
        assertThat(outboxRepo.findByDedupKey("plan-created-" + plan.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should prevent duplicate outbox messages (dedupKey uniqueness)")
    void shouldPreventDuplicateOutbox() {
        // Arrange: Save one Outbox message
        String dedupKey = "plan-created-123";
        outboxRepo.save(OutboxMessage.builder()
            .dedupKey(dedupKey)
            .eventType("PlanCreatedEvent")
            .status("PUBLISHED")
            .build());

        // Act: Attempt to create duplicate message
        PlanAggregate plan = PlanTestBuilder.aDefaultPlan().withId(123L).build();

        assertThatThrownBy(() -> coordinator.persistPlanWithOutbox(plan))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("dedupKey");
    }

    @Test
    @DisplayName("Should relay pending outbox messages")
    void shouldRelayPendingMessages() {
        // Arrange: Create PENDING outbox message
        OutboxMessage pending = outboxRepo.save(OutboxMessage.builder()
            .dedupKey("plan-created-999")
            .eventType("PlanCreatedEvent")
            .status("PENDING")
            .payload("{\"planId\":999}")
            .build());

        // Act: Execute Relay (simulate scheduled job)
        OutboxRelayOrchestrator relayOrchestrator = ...; // inject
        relayOrchestrator.relayPendingMessages();

        // Assert: Message status updated to PUBLISHED
        OutboxMessage relayed = outboxRepo.findById(pending.getId()).orElseThrow();
        assertThat(relayed.getStatus()).isEqualTo("PUBLISHED");
        assertThat(relayed.getPublishedAt()).isNotNull();
    }
}
```

**Key Testing Points for Outbox Pattern**:
- Test atomicity: business data + outbox message in same transaction
- Test rollback: both should rollback together on failure
- Test dedupKey uniqueness constraint
- Test relay mechanism (PENDING → PUBLISHED)
- Test idempotency at outbox level

---

### Transaction Boundary Testing

Test **transaction propagation** and **rollback scenarios**.

#### Example: Testing Transaction Rollback

```java
@SpringBootTest
@Transactional
class TransactionBoundaryTest {

    @Autowired
    private PlanIngestionOrchestrator orchestrator;

    @Autowired
    private PlanRepositoryPort planRepo;

    @Test
    @DisplayName("Should rollback entire transaction on failure")
    void shouldRollbackOnFailure() {
        // Arrange
        PlanIngestionContext context = new PlanIngestionContext(...);

        // Mock to throw exception during persistence
        doThrow(new RuntimeException("DB error"))
            .when(planRepo).save(any());

        // Act & Assert
        assertThatThrownBy(() -> orchestrator.ingestPlan(context))
            .isInstanceOf(RuntimeException.class);

        // Verify: No data persisted (transaction rolled back)
        assertThat(planRepo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Should support REQUIRES_NEW propagation")
    void shouldSupportRequiresNewPropagation() {
        // Test that REQUIRES_NEW creates independent transaction
        // Implementation depends on your orchestrator design
    }
}
```

---

### Concurrency & Optimistic Locking Testing

Test **concurrent updates** and **optimistic lock conflict resolution**.

#### Example: Testing Optimistic Lock Conflicts

```java
@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private PlanRepositoryPort planRepo;

    @Test
    @DisplayName("Should detect concurrent update conflicts")
    void shouldDetectConcurrentConflicts() {
        // Arrange: Save plan with version 1
        PlanAggregate plan = planRepo.save(PlanTestBuilder.aDefaultPlan()
            .withVersion(1L)
            .build());

        // Simulate concurrent update (version mismatch)
        PlanAggregate staleVersion = planRepo.findById(plan.getId()).orElseThrow();
        plan.updateStatus(PlanStatus.COMPLETED);  // Update original
        planRepo.save(plan);  // Save original (version 2)

        // Act: Try to update stale version (still version 1)
        staleVersion.updateStatus(PlanStatus.FAILED);

        // Assert: OptimisticLockingFailureException thrown
        assertThatThrownBy(() -> planRepo.save(staleVersion))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Should retry on optimistic lock conflict")
    void shouldRetryOnOptimisticLockConflict() {
        // Test retry logic for optimistic lock conflicts
        // Implementation depends on your retry mechanism
    }
}
```

---

## Test Data Management

**Purpose**: Manage test data creation and isolation efficiently.

### Test Data Builder Pattern

Use **fluent builders** to create test data with readable, maintainable code.

#### Example: Test Data Builder

```java
public class PlanTestBuilder {
    private Long id;
    private String planKey = "TEST:HARVEST:12345";
    private String provenanceCode = "TEST";
    private String operationCode = "HARVEST";
    private PlanStatus status = PlanStatus.DRAFT;
    private WindowSpec windowSpec = WindowSpec.ofSingle();
    private Long version = 1L;

    public static PlanTestBuilder aDefaultPlan() {
        return new PlanTestBuilder();
    }

    public PlanTestBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public PlanTestBuilder withProvenanceCode(String code) {
        this.provenanceCode = code;
        return this;
    }

    public PlanTestBuilder withStatus(PlanStatus status) {
        this.status = status;
        return this;
    }

    public PlanTestBuilder withVersion(Long version) {
        this.version = version;
        return this;
    }

    public PlanTestBuilder withWindowSpec(WindowSpec windowSpec) {
        this.windowSpec = windowSpec;
        return this;
    }

    public PlanAggregate build() {
        return PlanAggregate.create(
            100L,            // scheduleInstanceId
            planKey,
            provenanceCode,
            operationCode,
            "hash123",
            "{}",
            "{}",
            "confighash",
            windowSpec,
            "SINGLE",
            "{}"
        );
    }
}
```

#### Usage Example

```java
// Simple plan
PlanAggregate plan = aDefaultPlan().build();

// Customized plan
PlanAggregate pubmedPlan = aDefaultPlan()
    .withProvenanceCode("PUBMED")
    .withStatus(PlanStatus.READY)
    .withVersion(5L)
    .build();

// Plan with time window
PlanAggregate timePlan = aDefaultPlan()
    .withWindowSpec(WindowSpec.ofTime(
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-01-31T23:59:59Z")
    ))
    .build();
```

### Fixture Management

Organize test fixtures in dedicated classes or methods.

#### Example: Fixture Class

```java
public class TestFixtures {

    public static Provenance createProvenance(String code) {
        return new Provenance(
            null,
            code,
            code + " Name",
            "https://" + code.toLowerCase() + ".com",
            "UTC",
            "https://docs.com",
            true,
            "ACTIVE"
        );
    }

    public static PlanAggregate createPlanForProvenance(String provenanceCode) {
        return PlanTestBuilder.aDefaultPlan()
            .withProvenanceCode(provenanceCode)
            .build();
    }

    public static List<TaskAggregate> createTasksForSlice(Long sliceId, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> TaskTestBuilder.aTask()
                .withSliceId(sliceId)
                .withTaskKey("TASK-" + i)
                .build())
            .toList();
    }
}
```

### Test Data Isolation

Use `@Transactional` on test classes to auto-rollback after each test.

```java
@SpringBootTest
@Transactional  // Auto-rollback after each test
class MyIntegrationTest {

    @Test
    void testA() {
        // Create test data
        // Automatically rolled back after test
    }

    @Test
    void testB() {
        // Fresh database state (previous test rolled back)
    }
}
```

---

## Test Coverage Strategy

**Purpose**: Define coverage targets and measurement strategies.

### JaCoCo Configuration

Configure JaCoCo for automated coverage reporting.

#### Maven Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Layer-specific Coverage Targets

| Layer | Target Coverage | Rationale |
|-------|----------------|-----------|
| **Domain** | 90%+ | Core business logic - critical to test thoroughly |
| **Application** | 80%+ | Orchestration logic - important workflows |
| **Infrastructure** | 70%+ | Repository & converter - some boilerplate |
| **Adapter** | 75%+ | Controllers & jobs - validation & error handling |

### Critical Path Identification

Focus on high-value test coverage:

1. **Business-critical paths**:
   - Payment processing
   - User authentication
   - Data ingestion workflows

2. **Complex domain logic**:
   - Status calculation algorithms
   - Aggregation rules
   - Validation logic

3. **Error-prone areas**:
   - Concurrent updates
   - Event propagation
   - External API integration

### AI Testing Coverage Checklist

When generating tests, ensure coverage of:

```
□ Happy path (expected flow)
□ Edge cases (boundary values, empty lists, null)
□ Error scenarios (exceptions, validation failures)
□ Business rule violations
□ Concurrent scenarios (if applicable)
□ Idempotency (if applicable)
□ Transaction rollback (if @Transactional)
□ Event publishing (if event-driven)
```

---

## ArchUnit Tests

See [dependency-rules.md](dependency-rules.md) for complete ArchUnit patterns.

**Quick Example**:

```java
@AnalyzeClasses(packages = "com.patra.registry")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainLayerIsIndependent =
        classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat(
                resideInAnyPackage(
                    "java..",
                    "lombok..",
                    "cn.hutool..",
                    "com.patra.common..",
                    "..domain.."
                )
            );

    @ArchTest
    static final ArchRule adaptersDependOnApplication =
        classes()
            .that().resideInAPackage("..adapter..")
            .should().dependOnClassesThat(
                resideInAnyPackage(
                    "..app..",
                    "..domain.."
                )
            )
            .andShould().notDependOnClassesThat(
                resideInAPackage("..infra..")
            );
}
```

---

## Testing Best Practices

### 1. Test Naming Conventions

```java
// ✅ Good: Describes behavior
@Test
void shouldCreatePlanWhenValidContextProvided() { }

@Test
void shouldThrowExceptionWhenProvenanceNotFound() { }

// ❌ Bad: Generic names
@Test
void testCreatePlan() { }

@Test
void test1() { }
```

### 2. AAA Pattern (Arrange-Act-Assert)

```java
@Test
void shouldCalculateWindowDuration() {
    // Arrange (Given) - Setup test data
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    Instant end = Instant.parse("2024-01-31T23:59:59Z");
    WindowSpec.Time window = WindowSpec.ofTime(start, end);

    // Act (When) - Execute behavior
    long durationSeconds = window.to().getEpochSecond() - window.from().getEpochSecond();

    // Assert (Then) - Verify results
    assertThat(durationSeconds).isGreaterThan(0);
}
```

### 3. Use AssertJ for Fluent Assertions

```java
// ✅ Good (AssertJ - fluent and readable)
assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
assertThat(plan.getId()).isNotNull();
assertThat(plan.getProvenanceCode()).isEqualTo("PUBMED");

// ❌ Avoid (JUnit assertions - less readable)
assertNotNull(plan.getId());
assertTrue(plan.getStatus() == PlanStatus.DRAFT);
assertEquals("PUBMED", plan.getProvenanceCode());
```

### 4. Avoid Test Interdependence

```java
// ❌ Bad: Tests depend on execution order
@Test
void test1_createProvenance() {
    provenance = provenanceService.create("PUBMED");
}

@Test
void test2_updateProvenance() {
    provenanceService.update(provenance, "Updated");  // Depends on test1
}

// ✅ Good: Each test is independent
@Test
void shouldCreateProvenance() {
    Provenance provenance = provenanceService.create("PUBMED");
    assertThat(provenance).isNotNull();
}

@Test
void shouldUpdateProvenance() {
    Provenance provenance = createTestProvenance();  // Create fresh
    provenanceService.update(provenance, "Updated");
}
```

### 5. Test One Behavior Per Test

```java
// ❌ Bad: Testing multiple behaviors
@Test
void testProvenance() {
    Provenance p = create("PUBMED");
    assertThat(p.code()).isEqualTo("PUBMED");

    update(p, "Updated");
    assertThat(p.name()).isEqualTo("Updated");

    delete(p);
    assertThat(findByCode("PUBMED")).isEmpty();
}

// ✅ Good: One behavior per test
@Test
void shouldCreateProvenance() { ... }

@Test
void shouldUpdateProvenance() { ... }

@Test
void shouldDeleteProvenance() { ... }
```

### 6. Use DisplayName for Clarity

```java
@Test
@DisplayName("Should propagate Task → Slice → Plan status updates")
void shouldPropagateStatusUpdates() {
    // Test implementation
}
```

---

## Testing Checklists & Quick Reference

**Purpose**: Fast decision-making for AI when generating tests.

### Checklist: Testing an Orchestrator

When you see an **Orchestrator** class:

```
□ Mock all Coordinator dependencies
□ Mock all Port dependencies
□ Verify Coordinator call order using InOrder
□ Test transaction rollback scenario (@Transactional)
□ Test exception propagation chain
□ Test idempotency (if applicable)
□ Aim for 80%+ coverage
```

**Example pattern**:
```java
@ExtendWith(MockitoExtension.class)
class OrchestratorTest {
    @Mock private CoordinatorA coordinatorA;
    @Mock private CoordinatorB coordinatorB;
    @InjectMocks private MyOrchestrator orchestrator;

    @Test
    void shouldCallCoordinatorsInOrder() {
        orchestrator.execute(context);
        InOrder inOrder = inOrder(coordinatorA, coordinatorB);
        inOrder.verify(coordinatorA).doA(any());
        inOrder.verify(coordinatorB).doB(any());
    }
}
```

---

### Checklist: Testing an Event Handler

When you see **@TransactionalEventListener**:

```
□ Use @SpringBootTest (needs Spring context)
□ Use @Transactional for auto-rollback
□ Mock ApplicationEventPublisher
□ Verify AFTER_COMMIT phase event publishing
□ Test idempotency check (dedupKey query against Outbox)
□ Test OptimisticLockingFailureException handling
□ Verify event chain propagation (integration test)
□ Test concurrent event scenarios (optional)
```

**Example pattern**:
```java
@SpringBootTest
@Transactional
class EventHandlerTest {
    @Autowired private MyEventHandler handler;
    @MockBean private ApplicationEventPublisher publisher;
    @Autowired private OutboxMessageRepository outboxRepo;

    @Test
    void shouldPublishEventAfterCommit() {
        handler.onEvent(event);
        verify(publisher).publishEvent(any(NextEvent.class));
    }

    @Test
    void shouldNotProcessDuplicateEvent() {
        outboxRepo.save(OutboxMessage.builder().dedupKey("dup").build());
        handler.onEvent(eventWithDedupKey("dup"));
        verify(publisher, never()).publishEvent(any());
    }
}
```

---

### Checklist: Testing a Repository

When you see a **Repository implementation**:

```
□ Use TestContainers (real database)
□ Extend BaseIntegrationTest (or configure MySQL container)
□ Test basic CRUD operations
□ Test complex queries (Wrapper, pagination)
□ Test unique constraint violations
□ Test optimistic lock version conflicts
□ Clean test data (@Transactional auto-rollback)
```

**Example pattern**:
```java
@SpringBootTest
@Testcontainers
class RepositoryIntegrationTest extends BaseIntegrationTest {
    @Autowired private MyRepository repo;

    @Test
    void shouldSaveAndFind() {
        var entity = repo.save(createEntity());
        var found = repo.findById(entity.getId());
        assertThat(found).isPresent();
    }
}
```

---

### Checklist: Testing a Coordinator

When you see a **Coordinator** class:

```
□ Use @ExtendWith(MockitoExtension.class)
□ Mock all dependencies (ports, other coordinators)
□ Verify concern-specific logic
□ Test exception wrapping (infrastructure → domain)
□ Test edge cases (empty lists, null handling)
```

---

### Checklist: Testing a Converter

When you see **MapStruct** converter:

```
□ Test Domain → DO conversion
□ Test DO → Domain conversion (round-trip)
□ Test null field handling
□ Test list conversions
□ Test complex nested objects
□ Verify field name mappings
```

---

### Checklist: Testing an XXL-Job

When you see **@XxlJob**:

```
□ Use @ExtendWith(MockitoExtension.class)
□ Mock XxlJobHelper with MockedStatic
□ Test parameter parsing (getJobParam)
□ Test job execution flow
□ Test failure scenarios and error handling
□ Verify job logging (XxlJobHelper.log)
```

---

### Pattern Matching Quick Reference

| See This Code Feature | Immediately Know | Verify These Points | Example Section |
|-----------------------|------------------|---------------------|-----------------|
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | Integration test + Mock Publisher | Event publishing, idempotency, optimistic lock | [§4.3](#event-handler-testing) |
| `Orchestrator + multiple Coordinators` | Layered mocking | Call order (InOrder), exception wrapping | [§4.1](#orchestrator-testing) |
| `OutboxMessage.builder()...save()` | Integration test | Atomicity with business data, dedupKey | [§7.2](#outbox-pattern-testing) |
| `@XxlJob` | Mock XxlJobHelper | Parameter parsing, job flow | [§6.2](#xxl-job-testing) |
| `@RestController` + `@Valid` | @WebMvcTest + MockMvc | Validation failure, ProblemDetail | [§6.1](#rest-controller-testing) |
| `MapStruct` converter | Unit test | Bidirectional mapping, null handling | [§5.2](#converter-testing) |
| `@Transactional` | Integration test | Rollback scenarios, REQUIRES_NEW | [§7.3](#transaction-boundary-testing) |
| Aggregate with `version` field | Integration test | Concurrent update conflicts | [§7.4](#concurrency--optimistic-locking-testing) |

---

## Summary

**Testing Strategy**:
- **Domain**: Pure unit tests, no mocks
- **Application**: Mock ports, test orchestration
- **Infrastructure**: TestContainers, real database
- **Adapter**: MockMvc or job mocking, mock application layer
- **Integration**: End-to-end workflows with real database and events
- **Architecture**: ArchUnit for dependency validation

**Key Tools**:
- **JUnit 5**: Test framework
- **Mockito**: Mocking framework
- **AssertJ**: Fluent assertions
- **TestContainers**: Real database for integration tests
- **ArchUnit**: Architecture validation
- **JaCoCo**: Code coverage

**Testing Pyramid Targets**:
- 70% Unit Tests (Domain + Application)
- 25% Integration Tests (Infrastructure + Integration patterns)
- 5% E2E Tests (Critical workflows)

**See Also**:
- [dependency-rules.md](dependency-rules.md) - ArchUnit patterns
- [architecture-overview.md](architecture-overview.md) - Layer responsibilities
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Application layer patterns
- [event-driven-architecture.md](event-driven-architecture.md) - Event handling patterns
- [outbox-pattern.md](outbox-pattern.md) - Outbox implementation details
