# MyBatis-Plus 模式与数据库访问

**目的**: 六边形架构中使用 MyBatis-Plus 进行数据库访问的基础设施层模式。

---

## 目录

- [Infrastructure Layer Overview](#infrastructure-layer-overview)
- [Data Objects (DOs)](#data-objects-dos)
- [MyBatis-Plus Mappers](#mybatis-plus-mappers)
- [Repository Implementation Pattern](#repository-implementation-pattern)
- [MapStruct Converters](#mapstruct-converters)
- [MyBatis-Plus Query API](#mybatis-plus-query-api)
- [XML Mappers for Complex Queries](#xml-mappers-for-complex-queries)
- [Performance Patterns](#performance-patterns)
- [Key Principles](#key-principles)
- [Testing Infrastructure Layer](#testing-infrastructure-layer)

---

## 基础设施层 Overview

The Infrastructure layer implements **domain ports** (repository interfaces) and handles all external concerns like databases, message queues, and third-party APIs.

### 基础设施层 Responsibilities

1. **Implement domain repository ports** using MyBatis-Plus
2. **Convert between Domain models and Data Objects** (DOs) using MapStruct
3. **Execute database operations** (queries, inserts, updates, deletes)
4. **Handle JSON columns** using Jackson `JsonNode` with type handlers
5. **Never expose DOs outside the infrastructure layer** - always convert to domain models

### 基础设施层 Structure

```
patra-ingest-infra/
├── persistence/
│   ├── entity/          # Data Objects (DOs) with MyBatis-Plus annotations
│   ├── mapper/          # MyBatis-Plus mapper interfaces + XML mappers
│   ├── repository/      # Repository implementations (implement domain ports)
│   └── converter/       # MapStruct converters (DO ↔ Domain)
└── resources/
    └── mapper/          # XML mapper files for complex queries
```

---

## Data Objects (DOs)

### What is a Data Object (DO)?

A **Data Object** is an infrastructure layer class that maps **directly to database tables** using MyBatis-Plus annotations. DOs must **never** be exposed outside the infrastructure layer.

### Complete DO Example: PlanDO

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/entity/PlanDO.java`

```java
package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Plan blueprint DO</b> — table: <code>ing_plan</code>
 *
 * <p>Represents a single ingestion batch blueprint, capturing provenance configuration,
 * expression prototype, and slice strategy.
 *
 * <p>Notes:
 * <ul>
 *   <li><code>plan_key</code> is human-readable and idempotent (UK: uk_plan_key).
 *   <li><code>expr_proto_snapshot</code> and <code>provenance_config_snapshot</code>
 *       are stored as JSON snapshots for replay and comparison.
 *   <li><code>window_spec</code> stores window boundaries as JSON; supports multiple
 *       strategies (TIME/DATE/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE).
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)  // ✅ Table mapping
public class PlanDO extends BaseDO {  // ✅ Extends BaseDO (id, version, created_at, etc.)

  /** Related schedule instance ID. */
  @TableField("schedule_instance_id")
  private Long scheduleInstanceId;

  /** External idempotency key (human-readable). */
  @TableField("plan_key")
  private String planKey;

  /** Provenance code redundancy (for grouping by provenance). */
  @TableField("provenance_code")
  private String provenanceCode;

  /** Operation type code (DICT: ing_operation). */
  @TableField("operation_code")
  private String operationCode;

  /** Expression prototype hash (normalized AST fingerprint). */
  @TableField("expr_proto_hash")
  private String exprProtoHash;

  /** Expression prototype snapshot (JSON AST, without local conditions). */
  @TableField(value = "expr_proto_snapshot", typeHandler = JacksonTypeHandler.class)
  private JsonNode exprProtoSnapshot;  // ✅ JSON column mapped to JsonNode

  /** Provenance configuration snapshot (JSON, runtime-invariant parameters). */
  @TableField(value = "provenance_config_snapshot", typeHandler = JacksonTypeHandler.class)
  private JsonNode provenanceConfigSnapshot;

  /** Hash of provenance configuration snapshot (for change detection). */
  @TableField("provenance_config_hash")
  private String provenanceConfigHash;

  /** Slice strategy code (TIME/DATE/ID_RANGE/CURSOR, etc.). */
  @TableField("slice_strategy_code")
  private String sliceStrategyCode;

  /** Slice parameters snapshot (JSON; strategy-specific details). */
  @TableField(value = "slice_params", typeHandler = JacksonTypeHandler.class)
  private JsonNode sliceParams;

  /** Window boundary spec (JSON; schema varies by slice_strategy_code). */
  @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
  private JsonNode windowSpec;

  /**
   * Denormalized window start timestamp for TIME strategy (application-maintained).
   *
   * <p>This field is populated by the application layer when {@code slice_strategy_code = "TIME"}
   * to enable efficient time-range queries. It should be set to {@code null} for non-TIME strategies.
   */
  @TableField("window_from_ts")
  private Instant windowFromTs;

  /**
   * Denormalized window end timestamp for TIME strategy (application-maintained).
   */
  @TableField("window_to_ts")
  private Instant windowToTs;

  /** Status code (DICT: ing_plan_status). */
  @TableField("status_code")
  private String statusCode;
}
```

### Complete DO Example: OutboxMessageDO

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/entity/OutboxMessageDO.java`

```java
package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Outbox message DO</b> — table: <code>ing_outbox_message</code>
 *
 * <p>Semantics: Generic outbound messages persisted in the <strong>same transaction</strong>
 * as business data (task notifications / integration events). The relay scans this table only
 * and publishes to external channels (MQ/Webhook), avoiding hot business tables to ensure
 * minimal write-side intrusion and decoupled publishing.
 *
 * <p>Key rules:
 * <ul>
 *   <li>Idempotency: (<code>channel</code>, <code>dedup_key</code>) is unique
 *       (UK: uk_outbox_channel_dedup) to enable source-side dedup and safe retries.
 *   <li>Ordering/partitioning: <code>partition_key</code> is recommended as
 *       "<code>provenance:operation</code>" and leveraged by index
 *       <code>idx_outbox_partition(channel, partition_key, status_code)</code> to control
 *       parallelism and preserve order (e.g., <code>PUBMED:HARVEST</code>).
 *   <li>Scheduling/delay: <code>not_before</code> is the earliest publish time (UTC);
 *       NULL means publishable anytime.
 *   <li>Lease: <code>pub_lease_owner</code>/<code>pub_leased_until</code> prevent
 *       concurrent relays from processing the same row.
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

  /** Aggregate type (e.g., TASK/PLAN/...). */
  @TableField("aggregate_type")
  private String aggregateType;

  /** Aggregate root ID. */
  @TableField("aggregate_id")
  private Long aggregateId;

  /** Logical channel = target topic (e.g., <code>INGEST_TASK</code>). */
  @TableField("channel")
  private String channel;

  /** Semantic operation tag, e.g., <code>TASK_READY</code>. */
  @TableField("op_type")
  private String opType;

  /**
   * Partition/order routing key.
   *
   * <p>Recommended format: <code>"provenance:operation"</code>, e.g., <code>PUBMED:HARVEST</code>.
   */
  @TableField("partition_key")
  private String partitionKey;

  /**
   * Deduplication key.
   *
   * <p>Uniqueness constraint: must be unique within the same {@link #channel}
   * (UK: uk_outbox_channel_dedup).
   */
  @TableField("dedup_key")
  private String dedupKey;

  /** Minimal payload (JSON). */
  @TableField("payload_json")
  private JsonNode payloadJson;

  /** Extended headers (JSON), e.g., <code>correlationId</code>, tracing context. */
  @TableField("headers_json")
  private JsonNode headersJson;

  /** Earliest publish time (UTC). */
  @TableField("not_before")
  private Instant notBefore;

  /** Successful publish timestamp (UTC). */
  @TableField("published_at")
  private Instant publishedAt;

  /** Publish status code. */
  @TableField("status_code")
  private String statusCode;

  /** Publish retry count (incremented on failures). */
  @TableField("retry_count")
  private Integer retryCount;

  /** Next retry publish time (UTC). */
  @TableField("next_retry_at")
  private Instant nextRetryAt;

  /** Last publish error code (from MQ SDK or internal policy). */
  @TableField("error_code")
  private String errorCode;

  /** Last publish error detail (truncated). */
  @TableField("error_msg")
  private String errorMsg;

  /** Publisher lease owner (instance id / workerId). */
  @TableField("pub_lease_owner")
  private String pubLeaseOwner;

  /** Publisher lease expiry time (UTC). */
  @TableField("pub_leased_until")
  private Instant pubLeasedUntil;
}
```

### Key DO Patterns

| Annotation | Usage | Purpose |
|-----------|-------|---------|
| **@TableName** | `@TableName(value = "ing_plan", autoResultMap = true)` | Map to database table |
| **@TableField** | `@TableField("plan_key")` | Map to database column |
| **@TableField with typeHandler** | `@TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)` | Handle JSON columns |
| **Extends BaseDO** | `extends BaseDO` | Inherit id, version, created_at, updated_at, deleted fields |
| **JsonNode for JSON** | `private JsonNode payloadJson` | Type-safe JSON storage |

---

## MyBatis-Plus Mappers

### What is a MyBatis-Plus Mapper?

A **MyBatis-Plus Mapper** is an interface that extends `BaseMapper<T>` and provides:
- Built-in CRUD operations (insert, update, delete, selectById, etc.)
- Custom query methods (defined in interface + implemented in XML)

### Complete Mapper Example: PlanMapper

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/mapper/PlanMapper.java`

```java
package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Plan Mapper - data access operations for the plan table.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanMapper extends BaseMapper<PlanDO> {  // ✅ Extends BaseMapper

  /** Find a plan by its plan key. */
  PlanDO findByPlanKey(@Param("planKey") String planKey);

  /** Find plans by schedule instance id. */
  List<PlanDO> findByScheduleInstanceId(@Param("scheduleInstanceId") Long scheduleInstanceId);

  /** Find active plans for a given provenance and operation type. */
  List<PlanDO> findActiveByProvenanceAndOperation(
      @Param("provenanceCode") String provenanceCode,
      @Param("operationCode") String operationCode,
      @Param("statusCodes") List<String> statusCodes);

  /** Count if a plan key exists. */
  int countByPlanKey(@Param("planKey") String planKey);

  /** Count plans by status for a given provenance and operation. */
  long countByProvenanceAndOperationAndStatus(
      @Param("provenanceCode") String provenanceCode,
      @Param("operationCode") String operationCode,
      @Param("statusCode") String statusCode);

  /** Batch update plan status. */
  int batchUpdateStatus(
      @Param("planIds") List<Long> planIds,
      @Param("statusCode") String statusCode,
      @Param("remarks") String remarks);

  /** Soft-delete a plan by id. */
  int softDeleteById(@Param("planId") Long planId);
}
```

### Key Mapper Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Extend BaseMapper** | `extends BaseMapper<PlanDO>` | Get built-in CRUD operations |
| **@Param annotation** | `@Param("planKey") String planKey` | Named parameters for XML mapper |
| **Custom query methods** | `PlanDO findByPlanKey(...)` | Defined in interface, implemented in XML |
| **Batch operations** | `int batchUpdateStatus(List<Long> planIds, ...)` | Efficient bulk updates |
| **Return type conventions** | `int` for affected rows, `List<DO>` for multi-results | Clear intent |

---

## Repository Implementation Pattern

### What is a Repository Implementation?

A **Repository Implementation** is an infrastructure layer class that:
- Implements a **domain port** (repository interface from domain layer)
- Uses MyBatis-Plus mapper for database operations
- Uses MapStruct converter to convert between DOs and domain models

### Complete Repository Example: PlanRepositoryMpImpl

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/PlanRepositoryMpImpl.java`

```java
package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepository;  // ✅ Domain port interface
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus implementation of PlanRepository (Infrastructure layer).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Mapping between PlanAggregate and PlanDO
 *   <li>Idempotent query by planKey / existence check
 *   <li>Insert / update (no complex conditional updates; optimistic locking via MP version field)
 * </ul>
 *
 * Logging strategy:
 * <ul>
 *   <li>DEBUG: log key fields on insert/update (id, planKey)
 *   <li>INFO: avoid noisy high-frequency CRUD logs
 * </ul>
 *
 * Thread-safety: stateless singleton via dependency injection.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {  // ✅ Implements domain port

  /** Plan mapper. */
  private final PlanMapper planMapper;

  /** Aggregate-to-DO converter. */
  private final PlanConverter planConverter;

  /**
   * Saves a Plan: insert or update based on presence of ID.
   *
   * <p>Converts aggregate to DO and back to ensure version/auto-increment fields are reflected.
   *
   * @param plan aggregate (required)
   * @return persisted aggregate
   */
  @Override
  public PlanAggregate save(PlanAggregate plan) {
    // ✅ Convert domain aggregate to infrastructure DO
    PlanDO entity = planConverter.toEntity(plan);

    if (entity.getId() == null) {
      // ✅ Insert new record
      if (log.isDebugEnabled()) {
        log.debug("plan insert planKey={}", entity.getPlanKey());
      }
      planMapper.insert(entity);
    } else {
      // ✅ Update existing record (optimistic locking via version field)
      if (log.isDebugEnabled()) {
        log.debug("plan update id={} planKey={}", entity.getId(), entity.getPlanKey());
      }
      planMapper.updateById(entity);
    }

    // ✅ Convert DO back to domain aggregate (to get updated ID/version)
    return planConverter.toAggregate(entity);
  }

  /**
   * Finds a plan by planKey.
   *
   * @param planKey idempotent key (empty returns empty)
   */
  @Override
  public Optional<PlanAggregate> findByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return Optional.empty();
    }

    // ✅ Query via mapper
    PlanDO entity = planMapper.findByPlanKey(planKey);
    boolean found = entity != null;

    if (log.isDebugEnabled()) {
      log.debug("query plan by planKey={}, found={}", planKey, found);
    }

    // ✅ Convert DO to domain aggregate and wrap in Optional
    return Optional.ofNullable(entity).map(planConverter::toAggregate);
  }

  /**
   * Checks whether a planKey exists.
   *
   * @param planKey idempotent key
   */
  @Override
  public boolean existsByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return false;
    }
    return planMapper.countByPlanKey(planKey) > 0;
  }

  @Override
  public Optional<PlanAggregate> findById(Long planId) {
    if (planId == null) {
      return Optional.empty();
    }

    // ✅ Use built-in MyBatis-Plus method
    PlanDO entity = planMapper.selectById(planId);
    boolean found = entity != null;

    if (log.isDebugEnabled()) {
      log.debug("query plan by id={}, found={}", planId, found);
    }

    return Optional.ofNullable(entity).map(planConverter::toAggregate);
  }
}
```

### Complete Repository Example: OutboxMessageRepositoryMpImpl (Batch Operations)

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/OutboxMessageRepositoryMpImpl.java`

```java
package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus implementation for Outbox message persistence.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Initial message write (PENDING state) with idempotency via (channel, dedupKey) uniqueness
 *   <li>Fetch publishable messages by channel and availability window
 *   <li>Compete for distributed "publish rights" via optimistic locking + lease fields
 *   <li>Advance state based on publish results: PUBLISHED / DEFERRED / FAILED
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl implements OutboxMessageRepository {

  private final OutboxMessageMapper mapper;
  private final OutboxMessageConverter converter;

  /**
   * Batch saves (insert-only) Outbox messages in PENDING state.
   *
   * <p>No deduplication performed: caller must handle (channel, dedupKey) idempotency before insertion.
   *
   * <p>Logging: DEBUG level records batch size to reduce noise; no per-message logging.
   *
   * @param messages Message collection (null/empty ignored)
   */
  @Override
  public void saveAll(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox batch insert size={} firstChannel={}",
          messages.size(),
          messages.get(0).getChannel());
    }

    // ✅ Batch insert for performance
    for (OutboxMessage message : messages) {
      OutboxMessageDO entity = converter.toEntity(message);
      mapper.insert(entity);
    }
  }

  /**
   * Inserts or updates a single Outbox message.
   *
   * @param message Message (null ignored)
   */
  @Override
  public void saveOrUpdate(OutboxMessage message) {
    if (message == null) {
      return;
    }

    OutboxMessageDO entity = converter.toEntity(message);

    if (entity.getId() == null) {
      // ✅ Insert new message
      mapper.insert(entity);
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbox insert channel={} dedupKey={} id={}",
            message.getChannel(),
            message.getDedupKey(),
            entity.getId());
      }
    } else {
      // ✅ Update existing message (uses optimistic locking via version field)
      mapper.updateById(entity);
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbox update id={} channel={} dedupKey={}",
            entity.getId(),
            message.getChannel(),
            message.getDedupKey());
      }
    }
  }
}
```

### Key Repository Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Implement domain port** | `implements PlanRepository` | Follow Dependency Inversion Principle |
| **Inject mapper & converter** | `private final PlanMapper planMapper` | Separation of concerns |
| **Convert at boundaries** | `planConverter.toEntity(plan)` → operate → `planConverter.toAggregate(entity)` | Keep domain pure |
| **Return domain types** | `Optional<PlanAggregate>` | Never expose DOs outside infra |
| **Insert vs Update** | Check `entity.getId() == null` | Distinguish between new/existing |
| **Batch operations** | `for (Message m : messages) { mapper.insert(...) }` | Performance optimization |
| **DEBUG logging** | `if (log.isDebugEnabled())` | Avoid high-frequency noise |

---

## MapStruct Converters

### What is a MapStruct Converter?

A **MapStruct Converter** is an interface that defines bidirectional conversions between:
- **Domain models** (Aggregates, Entities, Value Objects)
- **Data Objects** (DOs)

MapStruct generates the implementation at compile time.

### Complete Converter Example: PlanConverter

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/converter/PlanConverter.java`

```java
package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import java.util.Map;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Plan aggregate {@link PlanAggregate} ↔ data object {@link PlanDO} converter.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter {

  // ========== Domain Aggregate → DO ==========

  @Mapping(
      target = "exprProtoSnapshot",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getExprProtoSnapshotJson()))")
  @Mapping(
      target = "provenanceConfigSnapshot",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getProvenanceConfigSnapshotJson()))")
  @Mapping(
      target = "sliceParams",
      expression =
          "java(com.patra.common.json.JsonNodeMappings.jsonStringToNode(aggregate.getSliceParamsJson()))")
  @Mapping(target = "statusCode", source = "status", qualifiedByName = "planStatusToCode")
  @Mapping(target = "windowSpec", source = "windowSpec", qualifiedByName = "windowSpecToJson")
  PlanDO toEntity(PlanAggregate aggregate);

  /**
   * Post-mapping hook to populate denormalized timestamp fields for TIME strategy.
   *
   * <p>This method extracts {@code from} and {@code to} timestamps from {@link WindowSpec.Time}
   * and populates the {@code windowFromTs} and {@code windowToTs} fields for query optimization.
   * For non-TIME strategies, these fields are set to {@code null}.
   *
   * @param aggregate source aggregate
   * @param entity target DO (will be mutated)
   */
  @AfterMapping  // ✅ Post-processing hook
  default void populateDenormalizedTimestamps(
      PlanAggregate aggregate, @MappingTarget PlanDO entity) {
    WindowSpec windowSpec = aggregate.getWindowSpec();

    if (windowSpec instanceof WindowSpec.Time timeWindow) {
      // ✅ Populate denormalized fields for query optimization
      entity.setWindowFromTs(timeWindow.from());
      entity.setWindowToTs(timeWindow.to());
    } else {
      entity.setWindowFromTs(null);
      entity.setWindowToTs(null);
    }
  }

  // ========== DO → Domain Aggregate ==========

  default PlanAggregate toAggregate(PlanDO entity) {
    return toPlanAggregate(entity);
  }

  static PlanAggregate toPlanAggregate(PlanDO entity) {
    if (entity == null) {
      return null;
    }

    // ✅ Convert status code to domain enum
    PlanStatus status = planStatusFromCode(entity.getStatusCode());
    long version = entity.getVersion() == null ? 0L : entity.getVersion();

    // ✅ Convert JSON to domain value object
    WindowSpec windowSpec = jsonToWindowSpec(entity.getWindowSpec());

    // ✅ Use domain factory method to restore aggregate
    return PlanAggregate.restore(
        entity.getId(),
        entity.getScheduleInstanceId(),
        entity.getPlanKey(),
        entity.getProvenanceCode(),
        entity.getOperationCode(),
        entity.getExprProtoHash(),
        JsonNodeMappings.jsonNodeToString(entity.getExprProtoSnapshot()),
        JsonNodeMappings.jsonNodeToString(entity.getProvenanceConfigSnapshot()),
        entity.getProvenanceConfigHash(),
        windowSpec,
        entity.getSliceStrategyCode(),
        JsonNodeMappings.jsonNodeToString(entity.getSliceParams()),
        status,
        version);
  }

  // ========== Custom Mapping Methods ==========

  @Named("planStatusToCode")
  static String planStatusToCode(PlanStatus status) {
    return status == null ? null : status.getCode();
  }

  static PlanStatus planStatusFromCode(String code) {
    return code == null ? PlanStatus.DRAFT : PlanStatus.fromCode(code);
  }

  @Named("windowSpecToJson")
  static com.fasterxml.jackson.databind.JsonNode windowSpecToJson(WindowSpec spec) {
    if (spec == null) {
      return null;
    }
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
    // ✅ Convert domain value object to JSON via toMap()
    return mapper.valueToTree(spec.toMap());
  }

  static WindowSpec jsonToWindowSpec(com.fasterxml.jackson.databind.JsonNode json) {
    if (json == null || json.isNull()) {
      return null;
    }
    ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
    @SuppressWarnings("unchecked")
    Map<String, Object> map = mapper.convertValue(json, Map.class);
    // ✅ Reconstruct domain value object via fromMap()
    return WindowSpec.fromMap(map);
  }
}
```

### Key MapStruct Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **@Mapper annotation** | `@Mapper(componentModel = "spring")` | Generate Spring bean |
| **@Mapping annotation** | `@Mapping(target = "statusCode", source = "status")` | Custom field mapping |
| **Expression mapping** | `expression = "java(...)"` | Complex transformations |
| **@Named methods** | `@Named("planStatusToCode")` | Reusable mapping logic |
| **@AfterMapping hook** | `@AfterMapping default void populate(...)` | Post-processing |
| **Static methods** | `static PlanAggregate toPlanAggregate(...)` | Manual conversion |
| **Domain factory methods** | `PlanAggregate.restore(...)` | Use domain patterns |

---

## MyBatis-Plus Query API

### When to Use Lambda Query API

✅ **Use LambdaQueryWrapper for:**
- Simple queries (1-2 conditions)
- Type-safe queries (compile-time checking)
- Dynamic conditions
- Single-table queries

❌ **Use XML Mapper for:**
- Complex queries (3+ conditions, joins)
- Batch updates with complex logic
- Custom SQL that doesn't fit the query builder

### LambdaQueryWrapper Examples

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

// ✅ Simple single condition
LambdaQueryWrapper<PlanDO> wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getPlanKey, "PUBMED_HARVEST_2024");
PlanDO plan = planMapper.selectOne(wrapper);

// ✅ Multiple conditions with AND
wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getProvenanceCode, "PUBMED")
       .eq(PlanDO::getOperationCode, "HARVEST")
       .in(PlanDO::getStatusCode, List.of("READY", "SLICING"));
List<PlanDO> plans = planMapper.selectList(wrapper);

// ✅ Dynamic conditions (skip null values)
wrapper = Wrappers.lambdaQuery();
wrapper.eq(provenanceCode != null, PlanDO::getProvenanceCode, provenanceCode)
       .eq(operationCode != null, PlanDO::getOperationCode, operationCode)
       .ge(fromDate != null, PlanDO::getWindowFromTs, fromDate)
       .le(toDate != null, PlanDO::getWindowToTs, toDate);
List<PlanDO> plans = planMapper.selectList(wrapper);

// ✅ Ordering and pagination
wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getProvenanceCode, "PUBMED")
       .orderByDesc(PlanDO::getCreatedAt)
       .last("LIMIT 10");  // Or use Page<PlanDO> with IPage
List<PlanDO> recentPlans = planMapper.selectList(wrapper);

// ✅ Count query
wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getProvenanceCode, "PUBMED")
       .eq(PlanDO::getStatusCode, "READY");
Long count = planMapper.selectCount(wrapper);
```

### LambdaUpdateWrapper Examples

```java
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

// ✅ Simple update
LambdaUpdateWrapper<PlanDO> updateWrapper = Wrappers.lambdaUpdate();
updateWrapper.set(PlanDO::getStatusCode, "COMPLETED")
             .set(PlanDO::getUpdatedAt, Instant.now())
             .eq(PlanDO::getId, planId);
int affected = planMapper.update(null, updateWrapper);

// ✅ Conditional update with version (optimistic locking)
updateWrapper = Wrappers.lambdaUpdate();
updateWrapper.set(PlanDO::getStatusCode, "FAILED")
             .eq(PlanDO::getId, planId)
             .eq(PlanDO::getVersion, currentVersion);  // ✅ Optimistic lock
int affected = planMapper.update(null, updateWrapper);
if (affected == 0) {
    throw new OptimisticLockException("Plan was modified by another transaction");
}
```

---

## XML Mappers for Complex Queries

### When to Use XML Mappers

✅ **Use XML for:**
- Queries with 3+ conditions
- Batch operations (batch insert/update)
- Joins or subqueries
- Custom result mapping
- Performance-critical queries that need tuning

### Complete XML Mapper Example: PlanMapper.xml

**File**: `patra-ingest/patra-ingest-infra/src/main/resources/mapper/PlanMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.PlanMapper">

  <!-- ✅ Result map for custom field mapping -->
  <resultMap id="PlanResultMap" type="com.patra.ingest.infra.persistence.entity.PlanDO">
    <id column="id" property="id"/>
  </resultMap>

  <!-- ✅ Simple select with single condition -->
  <select id="findByPlanKey" resultMap="PlanResultMap">
    SELECT * FROM patra_ingest.ing_plan
    WHERE plan_key = #{planKey}
      AND deleted = 0
  </select>

  <!-- ✅ Select with ordering -->
  <select id="findByScheduleInstanceId" resultMap="PlanResultMap">
    SELECT * FROM patra_ingest.ing_plan
    WHERE schedule_instance_id = #{scheduleInstanceId}
      AND deleted = 0
    ORDER BY created_at
  </select>

  <!-- ✅ Select with IN clause using foreach -->
  <select id="findActiveByProvenanceAndOperation" resultMap="PlanResultMap">
    SELECT * FROM patra_ingest.ing_plan
    WHERE provenance_code = #{provenanceCode}
      AND operation_code = #{operationCode}
      AND status_code IN
      <foreach collection="statusCodes" item="status" open="(" separator="," close=")">
        #{status}
      </foreach>
      AND deleted = 0
    ORDER BY created_at DESC
  </select>

  <!-- ✅ Count query -->
  <select id="countByPlanKey" resultType="int">
    SELECT COUNT(1) FROM patra_ingest.ing_plan
    WHERE plan_key = #{planKey}
      AND deleted = 0
  </select>

  <!-- ✅ Count with multiple conditions -->
  <select id="countByProvenanceAndOperationAndStatus" resultType="long">
    SELECT COUNT(1) FROM patra_ingest.ing_plan
    WHERE provenance_code = #{provenanceCode}
      AND operation_code = #{operationCode}
      AND status_code = #{statusCode}
      AND deleted = 0
  </select>

  <!-- ✅ Batch update with JSON_SET for remarks field -->
  <update id="batchUpdateStatus">
    UPDATE patra_ingest.ing_plan
    SET status_code = #{statusCode},
        record_remarks = JSON_SET(COALESCE(record_remarks, '{}'), '$.batchUpdateReason', #{remarks}),
        updated_at = NOW(),
        version = version + 1  <!-- ✅ Increment version for optimistic locking -->
    WHERE id IN
    <foreach collection="planIds" item="planId" open="(" separator="," close=")">
      #{planId}
    </foreach>
      AND deleted = 0
  </update>

  <!-- ✅ Soft delete (set deleted flag) -->
  <update id="softDeleteById">
    UPDATE patra_ingest.ing_plan
    SET deleted = 1,
        updated_at = NOW(),
        version = version + 1
    WHERE id = #{planId}
  </update>

</mapper>
```

### Key XML Mapper Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **namespace** | `<mapper namespace="...PlanMapper">` | Links to Java interface |
| **resultMap** | `<resultMap id="PlanResultMap" type="...PlanDO">` | Custom result mapping |
| **#{parameter}** | `WHERE plan_key = #{planKey}` | Safe parameter substitution |
| **foreach** | `<foreach collection="statusCodes" item="status">` | IN clauses |
| **Soft delete filter** | `AND deleted = 0` | Always filter soft-deleted records |
| **Version increment** | `version = version + 1` | Optimistic locking |
| **JSON operations** | `JSON_SET(record_remarks, '$.key', #{value})` | Partial JSON updates |

---

## Performance Patterns

### Pattern 1: Avoid N+1 Queries

❌ **BAD: N+1 Query Problem**

```java
// ❌ BAD: Fetches plans, then queries slices for each plan (N queries)
List<PlanDO> plans = planMapper.selectList(wrapper);  // 1 query

for (PlanDO plan : plans) {
    // ❌ N queries (one per plan)
    List<SliceDO> slices = sliceMapper.selectList(
        Wrappers.lambdaQuery(SliceDO.class)
            .eq(SliceDO::getPlanId, plan.getId()));

    // Process slices...
}
```

✅ **GOOD: Single Batch Query**

```java
// ✅ GOOD: Fetch all plans first
List<PlanDO> plans = planMapper.selectList(wrapper);  // 1 query

// ✅ Batch fetch all slices in one query
List<Long> planIds = plans.stream().map(PlanDO::getId).collect(Collectors.toList());
List<SliceDO> slices = sliceMapper.selectList(
    Wrappers.lambdaQuery(SliceDO.class)
        .in(SliceDO::getPlanId, planIds));  // 1 query

// ✅ Group slices by plan ID in memory
Map<Long, List<SliceDO>> slicesByPlan = slices.stream()
    .collect(Collectors.groupingBy(SliceDO::getPlanId));

// Process plans with their slices
for (PlanDO plan : plans) {
    List<SliceDO> planSlices = slicesByPlan.getOrDefault(plan.getId(), List.of());
    // Process...
}
```

### Pattern 2: Batch Inserts

❌ **BAD: Individual Inserts**

```java
// ❌ BAD: N database round trips
for (OutboxMessage message : messages) {
    OutboxMessageDO entity = converter.toEntity(message);
    mapper.insert(entity);  // Individual insert
}
```

✅ **GOOD: Batch Insert**

```java
// ✅ GOOD: Use MyBatis-Plus batch insert service
IService<OutboxMessageDO> service = new ServiceImpl<>(OutboxMessageMapper.class, OutboxMessageDO.class);
List<OutboxMessageDO> entities = messages.stream()
    .map(converter::toEntity)
    .collect(Collectors.toList());
service.saveBatch(entities, 100);  // Batch size 100
```

### Pattern 3: Use Proper Indexing

✅ **Index Strategy**

```sql
-- ✅ Unique index for idempotency
CREATE UNIQUE INDEX uk_plan_key ON ing_plan(plan_key);

-- ✅ Composite index for common queries
CREATE INDEX idx_plan_provenance_status
ON ing_plan(provenance_code, operation_code, status_code, deleted);

-- ✅ Covering index for time-range queries
CREATE INDEX idx_plan_time_range
ON ing_plan(slice_strategy_code, window_from_ts, window_to_ts, deleted);
```

### Pattern 4: Select Only Needed Columns

❌ **BAD: Select All Columns**

```java
// ❌ BAD: Fetches all columns (including large JSON fields)
List<PlanDO> plans = planMapper.selectList(wrapper);
```

✅ **GOOD: Select Specific Columns**

```java
// ✅ GOOD: Select only needed columns
LambdaQueryWrapper<PlanDO> wrapper = Wrappers.lambdaQuery();
wrapper.select(PlanDO::getId, PlanDO::getPlanKey, PlanDO::getStatusCode)
       .eq(PlanDO::getProvenanceCode, "PUBMED");
List<PlanDO> plans = planMapper.selectList(wrapper);
```

---

## Key Principles

### 基础设施层 Rules

| ✅ DO | ❌ DON'T |
|------|---------|
| Implement domain ports | Create new interfaces in infrastructure |
| Use MapStruct for conversions | Manual conversion with setters/getters |
| Return domain types from repositories | Return DOs from repositories |
| Use `@TableField` for all columns | Rely on auto-mapping |
| Handle JSON with `JsonNode` + type handlers | Store JSON as String |
| Use optimistic locking (`@Version`) | Rely on database locks |
| Log at DEBUG level for CRUD | Log at INFO for every query |
| Use batch operations for bulk inserts | Insert one by one |

### Checklist Before Committing

- [ ] **Repository implements domain port**: Never create infrastructure-specific interfaces
- [ ] **DOs stay in infrastructure**: Never expose DOs to application or adapter layers
- [ ] **MapStruct converters exist**: No manual conversion code
- [ ] **JSON columns use type handlers**: `@TableField(typeHandler = JacksonTypeHandler.class)`
- [ ] **Soft delete filtering**: Always include `AND deleted = 0`
- [ ] **Optimistic locking**: Use `@Version` field for concurrent updates
- [ ] **Batch operations**: Use batch insert/update for > 10 records
- [ ] **Indexes exist**: Add indexes for common query patterns

---

## Testing Infrastructure Layer

### Unit Testing Repositories

Use **H2 in-memory database** or **Testcontainers** with MySQL for repository tests.

```java
@SpringBootTest
@Transactional  // ✅ Rollback after each test
class PlanRepositoryMpImplTest {

  @Autowired
  private PlanRepository planRepository;

  @Test
  void should_save_and_find_plan_by_plan_key() {
    // Given
    WindowSpec window = WindowSpec.ofTime(
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-12-31T23:59:59Z"));

    PlanAggregate plan = PlanAggregate.create(
        12345L,
        "PUBMED_HARVEST_2024",
        "PUBMED",
        "HARVEST",
        "expr_hash_123",
        "{}",
        "{}",
        "config_hash_456",
        window,
        "TIME",
        "{}");

    // When
    PlanAggregate savedPlan = planRepository.save(plan);

    // Then
    assertThat(savedPlan.getId()).isNotNull();  // ID assigned

    // When
    Optional<PlanAggregate> found = planRepository.findByPlanKey("PUBMED_HARVEST_2024");

    // Then
    assertThat(found).isPresent();
    assertThat(found.get().getPlanKey()).isEqualTo("PUBMED_HARVEST_2024");
    assertThat(found.get().getStatus()).isEqualTo(PlanStatus.DRAFT);
  }

  @Test
  void should_return_empty_when_plan_key_not_found() {
    // When
    Optional<PlanAggregate> found = planRepository.findByPlanKey("NON_EXISTENT");

    // Then
    assertThat(found).isEmpty();
  }

  @Test
  void should_update_plan_and_increment_version() {
    // Given
    PlanAggregate plan = createAndSavePlan();
    Long originalVersion = plan.getVersion();

    // When
    plan.startSlicing();  // Change status
    PlanAggregate updated = planRepository.save(plan);

    // Then
    assertThat(updated.getVersion()).isGreaterThan(originalVersion);
    assertThat(updated.getStatus()).isEqualTo(PlanStatus.SLICING);
  }
}
```

### 测试 MapStruct Converters

```java
@SpringBootTest
class PlanConverterTest {

  @Autowired
  private PlanConverter planConverter;

  @Test
  void should_convert_aggregate_to_entity() {
    // Given
    WindowSpec window = WindowSpec.ofTime(
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-12-31T23:59:59Z"));

    PlanAggregate aggregate = PlanAggregate.create(
        12345L,
        "PUBMED_HARVEST_2024",
        "PUBMED",
        "HARVEST",
        "expr_hash_123",
        "{}",
        "{}",
        "config_hash_456",
        window,
        "TIME",
        "{}");

    // When
    PlanDO entity = planConverter.toEntity(aggregate);

    // Then
    assertThat(entity.getPlanKey()).isEqualTo("PUBMED_HARVEST_2024");
    assertThat(entity.getProvenanceCode()).isEqualTo("PUBMED");
    assertThat(entity.getStatusCode()).isEqualTo("DRAFT");

    // ✅ Check denormalized timestamp fields
    assertThat(entity.getWindowFromTs()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    assertThat(entity.getWindowToTs()).isEqualTo(Instant.parse("2024-12-31T23:59:59Z"));
  }

  @Test
  void should_convert_entity_to_aggregate() {
    // Given
    PlanDO entity = new PlanDO();
    entity.setId(1L);
    entity.setPlanKey("PUBMED_HARVEST_2024");
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setStatusCode("DRAFT");
    entity.setVersion(1L);

    // Set JSON fields...
    ObjectMapper mapper = new ObjectMapper();
    entity.setWindowSpec(mapper.valueToTree(Map.of(
        "strategy", "TIME",
        "window", Map.of("from", "2024-01-01T00:00:00Z", "to", "2024-12-31T23:59:59Z"))));

    // When
    PlanAggregate aggregate = planConverter.toAggregate(entity);

    // Then
    assertThat(aggregate.getId()).isEqualTo(1L);
    assertThat(aggregate.getPlanKey()).isEqualTo("PUBMED_HARVEST_2024");
    assertThat(aggregate.getStatus()).isEqualTo(PlanStatus.DRAFT);
    assertThat(aggregate.getVersion()).isEqualTo(1L);
  }
}
```

---

**相关文件：**
- [architecture-overview.md](architecture-overview.md) - Hexagonal Architecture overview
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - Domain layer patterns
- [complete-examples.md](complete-examples.md) - End-to-end feature examples
- [dependency-rules.md](dependency-rules.md) - Layer dependency rules

---

**📝 Status**: ✅ **已完成** - Comprehensive guide to MyBatis-Plus patterns and database access from patra-ingest infrastructure layer.
