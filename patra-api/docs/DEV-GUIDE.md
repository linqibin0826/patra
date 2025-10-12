# Development Guide

This guide provides **code recipes** and **development patterns** for extending the Papertrace system. Follow these patterns to maintain consistency with the Hexagonal + DDD architecture.

---

## Table of Contents

1. [Adding a New Use Case](#1-adding-a-new-use-case)
2. [Adding a New Aggregate](#2-adding-a-new-aggregate)
3. [Adding a New Domain Event](#3-adding-a-new-domain-event)
4. [Adding a New Repository](#4-adding-a-new-repository)
5. [Adding a New REST Endpoint](#5-adding-a-new-rest-endpoint)
6. [Adding Configuration Support](#6-adding-configuration-support)
7. [Error Handling Pattern](#7-error-handling-pattern)
8. [Testing Strategies](#8-testing-strategies)

---

## 1. Adding a New Use Case

**Use case** = Application-layer orchestration (e.g., "Create Plan", "Relay Outbox Messages").

### Step-by-Step Recipe

#### 1.1 Define Command DTO (in `app/usecase/{feature}/command/`)

```java
package com.patra.{service}.app.usecase.{feature}.command;

/**
 * Command for triggering {use case name}.
 * Immutable value object representing input parameters.
 */
public record {UseCaseName}Command(
    ProvenanceCode provenanceCode,
    OperationCode operationCode,
    Instant triggeredAt,
    // ... other parameters
) {
    // Validation logic if needed
    public {UseCaseName}Command {
        Objects.requireNonNull(provenanceCode, "provenanceCode required");
        Objects.requireNonNull(triggeredAt, "triggeredAt required");
    }
}
```

#### 1.2 Define Result DTO (in `app/usecase/{feature}/dto/`)

```java
package com.patra.{service}.app.usecase.{feature}.dto;

/**
 * Result of {use case name}.
 * Contains outcome summary for caller.
 */
public record {UseCaseName}Result(
    Long entityId,
    String status,
    int processedCount
) {}
```

#### 1.3 Create Orchestrator (in `app/usecase/{feature}/`)

```java
package com.patra.{service}.app.usecase.{feature};

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator for {use case name}.
 *
 * Responsibilities:
 * - Coordinate domain objects and repositories
 * - Manage transaction boundaries
 * - Convert domain exceptions to app exceptions
 * - Pull and publish domain events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class {UseCaseName}Orchestrator implements {UseCaseName}UseCase {

    private final {Domain}Repository repository;
    private final {External}Port externalPort;

    @Override
    @Transactional
    public {UseCaseName}Result execute({UseCaseName}Command command) {
        log.info("[{SERVICE}][APP] {use case} start, params={}", command);

        // Phase 1: Load domain aggregates
        {Aggregate} aggregate = repository.findById(command.id())
            .orElseThrow(() -> new {Entity}NotFoundException("Not found: " + command.id()));

        // Phase 2: Execute domain behavior
        aggregate.performBusinessAction(command.param());

        // Phase 3: Persist changes
        {Aggregate} saved = repository.save(aggregate);

        // Phase 4: Publish domain events (if any)
        List<DomainEvent> events = saved.pullDomainEvents();
        if (!events.isEmpty()) {
            eventPublisher.publish(events);
        }

        log.info("[{SERVICE}][APP] {use case} success, aggregateId={}", saved.getId());
        return new {UseCaseName}Result(saved.getId(), "SUCCESS", 1);
    }
}
```

**Key points**:
- Use `@Transactional` at orchestrator method level
- **Never put business logic in orchestrator** — delegate to domain aggregates
- Pull domain events AFTER persistence, BEFORE transaction commit
- Log at INFO for major milestones, DEBUG for diagnostic details

---

## 2. Adding a New Aggregate

**Aggregate** = Consistency boundary + transaction boundary (e.g., `PlanAggregate`, `TaskAggregate`).

### Step-by-Step Recipe

#### 2.1 Create Aggregate Class (in `domain/model/aggregate/`)

```java
package com.patra.{service}.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.{service}.domain.event.{Event}Event;

/**
 * Aggregate root for {entity name}.
 *
 * Encapsulates:
 * - Business invariants
 * - State transitions
 * - Domain events
 */
public class {Entity}Aggregate extends AggregateRoot<Long> {

    // Immutable fields (final)
    private final String code;
    private final String provenanceCode;

    // Mutable state
    private {Status}Enum status;
    private Instant updatedAt;

    // Private constructor (force use of factory methods)
    private {Entity}Aggregate(Long id, String code, String provenanceCode, {Status}Enum status) {
        super(id);
        this.code = Objects.requireNonNull(code, "code required");
        this.provenanceCode = provenanceCode;
        this.status = status != null ? status : {Status}Enum.DRAFT;
    }

    /**
     * Factory method for creating new aggregate.
     */
    public static {Entity}Aggregate create(String code, String provenanceCode) {
        return new {Entity}Aggregate(null, code, provenanceCode, {Status}Enum.DRAFT);
    }

    /**
     * Factory method for restoring from persistence.
     */
    public static {Entity}Aggregate restore(Long id, String code, String provenanceCode,
                                            {Status}Enum status, long version) {
        {Entity}Aggregate aggregate = new {Entity}Aggregate(id, code, provenanceCode, status);
        aggregate.assignVersion(version);
        return aggregate;
    }

    /**
     * Domain behavior: state transition with business rules.
     */
    public void activate() {
        if (this.status != {Status}Enum.DRAFT) {
            throw new IllegalStateException("Can only activate from DRAFT state");
        }
        this.status = {Status}Enum.ACTIVE;
        this.updatedAt = Instant.now();

        // Raise domain event
        addDomainEvent({Event}Event.of(getId(), code, Instant.now()));
    }

    // Getters (no setters for immutable fields)
    public String getCode() { return code; }
    public String getProvenanceCode() { return provenanceCode; }
    public {Status}Enum getStatus() { return status; }
}
```

**Key points**:
- Extend `AggregateRoot<ID>` from `patra-common`
- Use **factory methods** (`create` for new, `restore` for hydration)
- Encapsulate state transitions in **behavior methods** (no public setters)
- Raise domain events via `addDomainEvent()`

---

## 3. Adding a New Domain Event

**Domain Event** = Something significant happened in the domain (e.g., "Task Queued", "Plan Completed").

### Step-by-Step Recipe

#### 3.1 Create Event Class (in `domain/event/`)

```java
package com.patra.{service}.domain.event;

import com.patra.common.domain.DomainEvent;

import java.time.Instant;

/**
 * Domain event raised when {something happens}.
 *
 * Consumed by:
 * - Outbox relay (for async publishing)
 * - Local event handlers (same service)
 */
public record {EventName}Event(
    Long entityId,
    String code,
    Instant occurredAt
) implements DomainEvent {

    /**
     * Factory method for convenience.
     */
    public static {EventName}Event of(Long entityId, String code, Instant occurredAt) {
        return new {EventName}Event(entityId, code, occurredAt != null ? occurredAt : Instant.now());
    }

    @Override
    public String eventType() {
        return "{service}.{entity}.{action}";  // e.g., "ingest.task.queued"
    }
}
```

#### 3.2 Publish Event in Aggregate

```java
public class {Entity}Aggregate extends AggregateRoot<Long> {

    public void performAction() {
        // ... state change logic

        // Raise event
        addDomainEvent({EventName}Event.of(getId(), code, Instant.now()));
    }
}
```

#### 3.3 Collect and Publish in Orchestrator

```java
@Transactional
public {UseCase}Result execute({UseCase}Command command) {
    {Entity}Aggregate aggregate = repository.findById(command.id());
    aggregate.performAction();

    {Entity}Aggregate saved = repository.save(aggregate);

    // Pull events and publish
    List<DomainEvent> events = saved.pullDomainEvents();
    outboxPublisher.publish(events);  // Converts to OutboxMessage + persists

    return new {UseCase}Result(saved.getId());
}
```

---

## 4. Adding a New Repository

**Repository** = Port interface (domain) + Implementation (infra).

### Step-by-Step Recipe

#### 4.1 Define Port Interface (in `domain/port/`)

```java
package com.patra.{service}.domain.port;

import com.patra.{service}.domain.model.aggregate.{Entity}Aggregate;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for {entity} domain objects.
 *
 * Abstracts persistence concerns from domain.
 */
public interface {Entity}Repository {

    /**
     * Save or update aggregate.
     */
    {Entity}Aggregate save({Entity}Aggregate aggregate);

    /**
     * Find by primary key.
     */
    Optional<{Entity}Aggregate> findById(Long id);

    /**
     * Find by business key.
     */
    Optional<{Entity}Aggregate> findByCode(String code);

    /**
     * Batch save.
     */
    List<{Entity}Aggregate> saveAll(List<{Entity}Aggregate> aggregates);
}
```

#### 4.2 Create DO (Data Object) (in `infra/persistence/entity/`)

```java
package com.patra.{service}.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis-Plus entity for {table_name}.
 */
@Data
@TableName("{table_name}")
public class {Entity}DO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String provenanceCode;
    private String status;

    @Version
    private Long version;  // Optimistic lock

    private Instant createdAt;
    private Instant updatedAt;
}
```

#### 4.3 Create Mapper (in `infra/persistence/mapper/`)

```java
package com.patra.{service}.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.{service}.infra.persistence.entity.{Entity}DO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface {Entity}Mapper extends BaseMapper<{Entity}DO> {
    // Custom queries if needed
}
```

#### 4.4 Create Converter (in `infra/persistence/converter/`)

```java
package com.patra.{service}.infra.persistence.converter;

import com.patra.{service}.domain.model.aggregate.{Entity}Aggregate;
import com.patra.{service}.infra.persistence.entity.{Entity}DO;
import org.mapstruct.Mapper;

/**
 * Converter between domain aggregate and persistence entity.
 */
@Mapper(componentModel = "spring")
public interface {Entity}Converter {

    {Entity}DO toEntity({Entity}Aggregate aggregate);

    {Entity}Aggregate toDomain({Entity}DO entity);

    List<{Entity}Aggregate> toDomainList(List<{Entity}DO> entities);
}
```

#### 4.5 Implement Repository (in `infra/persistence/repository/`)

```java
package com.patra.{service}.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.{service}.domain.model.aggregate.{Entity}Aggregate;
import com.patra.{service}.domain.port.{Entity}Repository;
import com.patra.{service}.infra.persistence.entity.{Entity}DO;
import com.patra.{service}.infra.persistence.mapper.{Entity}Mapper;
import com.patra.{service}.infra.persistence.converter.{Entity}Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class {Entity}RepositoryMpImpl implements {Entity}Repository {

    private final {Entity}Mapper mapper;
    private final {Entity}Converter converter;

    @Override
    public {Entity}Aggregate save({Entity}Aggregate aggregate) {
        {Entity}DO entity = converter.toEntity(aggregate);

        if (aggregate.isTransient()) {
            mapper.insert(entity);  // Auto-fills id
            aggregate.assignId(entity.getId());
        } else {
            mapper.updateById(entity);  // Optimistic lock via @Version
        }

        return aggregate;
    }

    @Override
    public Optional<{Entity}Aggregate> findById(Long id) {
        {Entity}DO entity = mapper.selectById(id);
        return Optional.ofNullable(entity).map(converter::toDomain);
    }

    @Override
    public Optional<{Entity}Aggregate> findByCode(String code) {
        LambdaQueryWrapper<{Entity}DO> query = new LambdaQueryWrapper<>();
        query.eq({Entity}DO::getCode, code);
        {Entity}DO entity = mapper.selectOne(query);
        return Optional.ofNullable(entity).map(converter::toDomain);
    }

    @Override
    public List<{Entity}Aggregate> saveAll(List<{Entity}Aggregate> aggregates) {
        // Batch insert for performance
        List<{Entity}DO> entities = aggregates.stream()
            .map(converter::toEntity)
            .toList();

        entities.forEach(mapper::insert);

        // Assign generated IDs back to aggregates
        for (int i = 0; i < aggregates.size(); i++) {
            aggregates.get(i).assignId(entities.get(i).getId());
        }

        return aggregates;
    }
}
```

---

## 5. Adding a New REST Endpoint

### Step-by-Step Recipe

#### 5.1 Define API Contract (in `{service}-api/rpc/dto/`)

```java
package com.patra.{service}.api.rpc.dto;

/**
 * Response DTO for {endpoint name}.
 * Used by Feign clients.
 */
public record {Entity}Resp(
    Long id,
    String code,
    String status
) {}
```

#### 5.2 Define API Endpoint Interface (in `{service}-api/rpc/endpoint/`)

```java
package com.patra.{service}.api.rpc.endpoint;

import org.springframework.web.bind.annotation.*;

/**
 * Internal API contract for {entity} operations.
 */
@RequestMapping("/internal/{entity}")
public interface {Entity}Endpoint {

    @GetMapping("/{id}")
    {Entity}Resp getById(@PathVariable Long id);

    @GetMapping
    List<{Entity}Resp> list();
}
```

#### 5.3 Define Feign Client (in `{service}-api/rpc/client/`)

```java
package com.patra.{service}.api.rpc.client;

import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign client for calling {service} internal APIs.
 */
@FeignClient(name = "patra-{service}", contextId = "{entity}Client")
public interface {Entity}Client extends {Entity}Endpoint {
}
```

#### 5.4 Implement Controller (in `{service}-adapter/inbound/rest/feign/`)

```java
package com.patra.{service}.adapter.inbound.rest.feign;

import com.patra.{service}.api.rpc.client.{Entity}Client;
import com.patra.{service}.api.rpc.dto.{Entity}Resp;
import com.patra.{service}.app.service.{Entity}AppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {entity} internal API.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class {Entity}ClientImpl implements {Entity}Client {

    private final {Entity}AppService appService;
    private final {Entity}ApiConverter converter;

    @Override
    public {Entity}Resp getById(Long id) {
        log.debug("[{SERVICE}][ADAPTER] get {entity} id={}", id);
        return appService.findById(id)
            .map(converter::toResp)
            .orElseThrow(() -> new {Entity}NotFoundException("Not found: " + id));
    }

    @Override
    public List<{Entity}Resp> list() {
        log.debug("[{SERVICE}][ADAPTER] list {entity}s");
        return appService.listAll().stream()
            .map(converter::toResp)
            .toList();
    }
}
```

---

## 6. Adding Configuration Support

For new **operational configurations** (like HTTP timeouts, retry policies):

### Recipe

1. **Domain VO** (in `registry-domain/model/vo/`): Define value object
2. **Domain Query** (in `registry-domain/model/read/`): Define read model
3. **Port method** (in `ProvenanceConfigRepository`): Add query method
4. **DO class** (in `registry-infra/entity/`): Create MyBatis table entity
5. **Mapper** (in `registry-infra/mapper/`): Add MyBatis mapper
6. **Repository impl**: Implement port method
7. **Aggregate**: Add field to `ProvenanceConfiguration`

**Example**: See `RetryConfig` implementation in `patra-registry`.

---

## 7. Error Handling Pattern

### Domain Exceptions

```java
// In domain/exception/
public class {Entity}NotFoundException extends RegistryException {
    public {Entity}NotFoundException(String message) {
        super(ErrorCodes.{ENTITY}_NOT_FOUND, message);
    }
}
```

### Application Exceptions

```java
// In app/exception/
public class {UseCase}ValidationException extends ApplicationException {
    public enum Reason { INVALID_WINDOW, CAPACITY_EXCEEDED }

    public {UseCase}ValidationException(String message, Reason reason, Throwable cause) {
        super(ErrorCodes.{USECASE}_VALIDATION_FAILED, message, cause);
        this.reason = reason;
    }
}
```

### Adapter Error Mapping

```java
// In adapter/config/
@ControllerAdvice
public class ErrorMappingContributor implements ProblemDetailContributor {

    @Override
    public ProblemDetail handle(Exception ex) {
        if (ex instanceof {Entity}NotFoundException nfe) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, nfe.getMessage());
        }
        return null;  // Let default handler take over
    }
}
```

---

## 8. Testing Strategies

### Unit Tests (Domain Layer)

```java
@Test
void testAggregateStateTransition() {
    // Given
    PlanAggregate plan = PlanAggregate.create(...);

    // When
    plan.startSlicing();

    // Then
    assertEquals(PlanStatus.SLICING, plan.getStatus());

    // Verify domain event
    List<DomainEvent> events = plan.peekDomainEvents();
    assertEquals(1, events.size());
    assertTrue(events.get(0) instanceof PlanSlicingStartedEvent);
}
```

### Integration Tests (Repository)

```java
@SpringBootTest
@Transactional
class {Entity}RepositoryTest {

    @Autowired
    private {Entity}Repository repository;

    @Test
    void testSaveAndFind() {
        // Given
        {Entity}Aggregate aggregate = {Entity}Aggregate.create("TEST_CODE");

        // When
        {Entity}Aggregate saved = repository.save(aggregate);
        Optional<{Entity}Aggregate> found = repository.findById(saved.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("TEST_CODE", found.get().getCode());
    }
}
```

### API Tests (Controller)

```java
@WebMvcTest({Entity}ClientImpl.class)
class {Entity}ClientTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private {Entity}AppService appService;

    @Test
    void testGetById() throws Exception {
        // Given
        when(appService.findById(1L)).thenReturn(Optional.of(queryDto));

        // When + Then
        mockMvc.perform(get("/internal/{entity}/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("TEST_CODE"));
    }
}
```

---

## Development Workflow

### Standard 7-Step Process

1. **Confirm inputs**: Target module, contracts, use case signature
2. **Define/improve Domain**: Aggregates, VOs, events (pure Java)
3. **Implement App orchestration**: Transaction boundaries (no business logic)
4. **Implement Infra**: MyBatis-Plus + MapStruct converters
5. **Implement Adapter**: Validation + error mapping + trace propagation
6. **Self-check**: Run `mvn -q -DskipTests compile` to verify compilation
7. **Handoff**: Submit minimal diff for review

---

## Code Style Guidelines

- **Naming**: `*Aggregate` (aggregates), `*Orchestrator` (use cases), `*RepositoryMpImpl` (MyBatis impl)
- **Logging**: English, parameterized (`log.info("Processing {}", id)`)
- **Records**: Prefer `record` for immutable DTOs/VOs
- **Null safety**: Use `Optional` for repository queries, `@NonNull` for required params
- **Comments**: Javadoc for public APIs, inline comments for complex logic

---

**Last Updated**: 2025-01-12
**Author**: Claude (via Papertrace documentation project)
