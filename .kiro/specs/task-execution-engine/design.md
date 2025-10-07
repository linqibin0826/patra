# Design Document

## Overview

通用数据采集任务执行引擎是 `patra-ingest` 模块的核心组件，负责协调和执行从外部数据源（PubMed、EPMC、Crossref 等）采集数据的完整流程。该引擎采用**事件驱动架构 + 策略模式 + 六边形架构**，通过消息队列触发、分布式租约协调、批次拆分执行、游标推进等机制，实现可靠、可追溯、可扩展的数据采集。

### 核心设计目标

1. **可靠性**：通过多层幂等保障、事务机制和重试策略，确保数据不丢失、不重复
2. **可扩展性**：通过策略模式和配置驱动，支持新增数据源只需实现接口和配置注册
3. **可观测性**：通过完整的追踪链路、详细的日志和统计信息，支持问题定位和性能分析
4. **容错性**：通过部分失败容忍、断点续传和多层重试，提高任务执行成功率
5. **分布式协调**：通过 CAS 租约机制和心跳续租，支持多节点安全执行

### 技术选型

- **消息队列**：RocketMQ（任务就绪通知、Outbox 消息发布）
- **分布式协调**：数据库 CAS + 租约机制（无需引入 ZooKeeper/etcd）
- **对象存储**：MinIO（原始数据存储）
- **数据库**：MySQL 8.0（任务状态、批次记录、游标管理）
- **追踪**：SkyWalking（分布式调用链追踪）
- **调度**：XXL-Job（定时触发任务就绪消息）

## Architecture

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         Scheduler (XXL-Job)                      │
│                    定时触发任务就绪消息                            │
└────────────────────────────┬────────────────────────────────────┘
                             │ publish
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Message Queue (RocketMQ)                    │
│                    Topic: ingest.task.ready                      │
└────────────────────────────┬────────────────────────────────────┘
                             │ consume
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Task Execution Engine (Core)                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 0: 幂等检查 (IdempotencyChecker)                    │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 1: 租约抢占 (LeaseAcquisitionService)              │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 2: 会话初始化 (ExecutionSessionInitializer)        │   │
│  │          + 心跳续租 (HeartbeatRenewalService)            │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 3: 配置还原 (ConfigSnapshotRestorer)               │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 4: 表达式编译 (ExpressionCompiler)                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 5: 批次规划 (BatchPlanner - Strategy)              │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 6: 批次执行 (BatchExecutor - Strategy)             │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 7: 游标推进 (CursorAdvancer - Strategy)            │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Step 8: 状态更新 (ExecutionFinalizer)                   │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ├─────────────────┐
                             │                 │
                             ▼                 ▼
              ┌─────────────────────┐     ┌─────────────────────┐
              │  MySQL Database     │     │  MinIO Storage      │
              │  - ing_task         │     │  - Raw Data Files   │
              │  - ing_task_run     │     │  - Compressed JSON  │
              │  - ing_task_run_batch│    └─────────────────────┘
              │  - ing_cursor       │
              │  - ing_cursor_event │
              │  - outbox           │
              └─────────────────────┘
```

### 六边形架构分层

```
┌─────────────────────────────────────────────────────────────────┐
│                         Adapter Layer                            │
│  - TaskReadyMessageConsumer (MQ 消费者)                          │
│  - TaskExecutionScheduler (XXL-Job 调度器)                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────────┐
│                       Application Layer                          │
│  - TaskExecutionOrchestrator (主编排器)                          │
│  - ExecutionStepCoordinator (步骤协调器)                         │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────────┐
│                         Domain Layer                             │
│  - TaskAggregate (任务聚合根)                                    │
│  - TaskRunAggregate (执行记录聚合根)                             │
│  - RunBatchAggregate (批次聚合根)                                │
│  - CursorAggregate (游标聚合根)                                  │
│  - Domain Events (TaskStartedEvent, BatchCompletedEvent, etc.)   │
│  - Domain Ports (TaskRepository, CursorRepository, etc.)         │
└─────────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────────┐
│                      Infrastructure Layer                        │
│  - TaskRepositoryImpl (MyBatis-Plus)                             │
│  - CursorRepositoryImpl (MyBatis-Plus)                           │
│  - ProvenanceClientAdapter (调用 patra-spring-boot-starter-     │
│    provenance)                                                   │
│  - MinIOStorageAdapter (对象存储)                                │
│  - OutboxPublisherImpl (Outbox 消息发布)                         │
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. 核心编排器 (TaskExecutionOrchestrator)

**职责**：协调整个任务执行流程的 8 个步骤，处理异常和事务边界。

**接口定义**：

```java
public interface TaskExecutionUseCase {
    /**
     * 执行任务
     * @param command 任务执行命令
     * @return 执行结果
     */
    TaskExecutionResult execute(TaskExecutionCommand command);
}

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionOrchestrator implements TaskExecutionUseCase {
    private final IdempotencyChecker idempotencyChecker;
    private final LeaseAcquisitionService leaseAcquisitionService;
    private final ExecutionSessionInitializer sessionInitializer;
    private final ConfigSnapshotRestorer configRestorer;
    private final ExpressionCompiler expressionCompiler;
    private final BatchPlannerRegistry batchPlannerRegistry;
    private final BatchExecutorRegistry batchExecutorRegistry;
    private final CursorAdvancerRegistry cursorAdvancerRegistry;
    private final ExecutionFinalizer executionFinalizer;
    
    @Override
    public TaskExecutionResult execute(TaskExecutionCommand command) {
        // 步骤 0-8 的编排逻辑
    }
}
```

### 2. 幂等检查器 (IdempotencyChecker)

**职责**：检查任务是否已成功完成，避免重复执行。

**接口定义**：

```java
public interface IdempotencyChecker {
    /**
     * 检查任务是否已完成
     * @param taskId 任务 ID
     * @param idempotentKey 幂等键
     * @return true 表示已完成，false 表示未完成
     */
    boolean isAlreadyCompleted(Long taskId, String idempotentKey);
}
```

### 3. 租约获取服务 (LeaseAcquisitionService)

**职责**：通过 CAS 机制抢占任务执行权。

**接口定义**：

```java
public interface LeaseAcquisitionService {
    /**
     * 尝试获取任务租约
     * @param taskId 任务 ID
     * @param leaseOwner 租约持有者标识
     * @param leaseDuration 租约时长（秒）
     * @return 租约获取结果
     */
    LeaseAcquisitionResult tryAcquire(Long taskId, String leaseOwner, int leaseDuration);
}
```


### 4. 执行会话初始化器 (ExecutionSessionInitializer)

**职责**：初始化执行会话，创建 TaskRun 记录，启动心跳续租。

**接口定义**：

```java
public interface ExecutionSessionInitializer {
    /**
     * 初始化执行会话
     * @param context 执行上下文
     * @return 执行会话
     */
    ExecutionSession initialize(ExecutionContext context);
}

public interface HeartbeatRenewalService {
    /**
     * 启动心跳续租
     * @param taskId 任务 ID
     * @param leaseOwner 租约持有者
     * @param renewalInterval 续租间隔（秒）
     * @return 心跳任务句柄
     */
    ScheduledFuture<?> startHeartbeat(Long taskId, String leaseOwner, int renewalInterval);
    
    /**
     * 停止心跳续租
     * @param heartbeatHandle 心跳任务句柄
     */
    void stopHeartbeat(ScheduledFuture<?> heartbeatHandle);
}
```

### 5. 配置快照还原器 (ConfigSnapshotRestorer)

**职责**：从任务记录中还原配置快照并校验哈希。

**接口定义**：

```java
public interface ConfigSnapshotRestorer {
    /**
     * 还原配置快照
     * @param taskId 任务 ID
     * @return 配置快照
     */
    ConfigSnapshot restore(Long taskId);
}

public record ConfigSnapshot(
    ProvenanceConfig provenanceConfig,
    ExprSnapshot exprSnapshot,
    String configHash
) {
    public void validateHash() {
        String calculatedHash = calculateHash();
        if (!calculatedHash.equals(configHash)) {
            throw new ConfigurationTamperedException("配置哈希不匹配");
        }
    }
}
```

### 6. 表达式编译器 (ExpressionCompiler)

**职责**：将表达式快照编译为目标数据源的查询参数。

**接口定义**：

```java
public interface ExpressionCompiler {
    /**
     * 编译表达式
     * @param exprSnapshot 表达式快照
     * @param provenanceCode 数据源代码
     * @return 编译后的查询参数
     */
    QueryParameters compile(ExprSnapshot exprSnapshot, String provenanceCode);
}

// 策略接口
public interface ExpressionCompilerStrategy {
    /**
     * 是否支持该数据源
     */
    boolean supports(String provenanceCode);
    
    /**
     * 编译表达式
     */
    QueryParameters compile(ExprSnapshot exprSnapshot);
}
```

### 7. 批次规划器注册表 (BatchPlannerRegistry)

**职责**：根据数据源代码选择对应的批次规划器。

**接口定义**：

```java
public interface BatchPlannerRegistry {
    /**
     * 获取批次规划器
     * @param provenanceCode 数据源代码
     * @return 批次规划器
     */
    BatchPlanner getPlanner(String provenanceCode);
}

// 策略接口
public interface BatchPlanner {
    /**
     * 规划批次
     * @param context 规划上下文
     * @return 批次计划
     */
    BatchPlan plan(BatchPlanningContext context);
}

// PubMed 实现
@Component
public class PubMedBatchPlanner implements BatchPlanner {
    @Override
    public BatchPlan plan(BatchPlanningContext context) {
        // 1. 调用 ESearch API 获取总数和 WebEnv
        // 2. 计算批次数 = min(总量 ÷ 每批大小, maxBatchesPerExecution)
        // 3. 生成批次记录
    }
}

// EPMC 实现
@Component
public class EpmcBatchPlanner implements BatchPlanner {
    @Override
    public BatchPlan plan(BatchPlanningContext context) {
        // 1. 调用初始 Search API 获取第一页和 cursor
        // 2. 生成批次记录（每批使用上一批的 cursor）
    }
}
```

### 8. 批次执行器注册表 (BatchExecutorRegistry)

**职责**：根据数据源代码选择对应的批次执行器。

**接口定义**：

```java
public interface BatchExecutorRegistry {
    /**
     * 获取批次执行器
     * @param provenanceCode 数据源代码
     * @return 批次执行器
     */
    BatchExecutor getExecutor(String provenanceCode);
}

// 策略接口
public interface BatchExecutor {
    /**
     * 执行批次
     * @param context 执行上下文
     * @return 批次执行结果
     */
    BatchExecutionResult execute(BatchExecutionContext context);
}

// PubMed 实现
@Component
public class PubMedBatchExecutor implements BatchExecutor {
    private final PubMedClient pubMedClient;
    private final MinIOStorageAdapter storageAdapter;
    private final OutboxPublisher outboxPublisher;
    
    @Override
    public BatchExecutionResult execute(BatchExecutionContext context) {
        // 1. 幂等检查
        // 2. 更新批次状态为 RUNNING
        // 3. 调用 EFetch API
        // 4. 解析响应
        // 5. 压缩数据
        // 6. 上传 MinIO
        // 7. 写入 Outbox（事务）
        // 8. 更新批次状态为 SUCCEEDED
    }
}
```

### 9. 游标推进器注册表 (CursorAdvancerRegistry)

**职责**：根据操作类型选择对应的游标推进策略。

**接口定义**：

```java
public interface CursorAdvancerRegistry {
    /**
     * 获取游标推进器
     * @param operationType 操作类型
     * @return 游标推进器
     */
    CursorAdvancer getAdvancer(OperationType operationType);
}

// 策略接口
public interface CursorAdvancer {
    /**
     * 推进游标
     * @param context 推进上下文
     * @return 推进结果
     */
    CursorAdvancementResult advance(CursorAdvancementContext context);
}

// 时间型游标推进器
@Component
public class TimestampCursorAdvancer implements CursorAdvancer {
    @Override
    public CursorAdvancementResult advance(CursorAdvancementContext context) {
        // 1. 从所有 SUCCEEDED 批次的 stats 中提取最大时间戳
        // 2. 使用乐观锁更新游标表
        // 3. 插入游标事件
    }
}

// ID 型游标推进器
@Component
public class IdCursorAdvancer implements CursorAdvancer {
    @Override
    public CursorAdvancementResult advance(CursorAdvancementContext context) {
        // 1. 从所有 SUCCEEDED 批次的 stats 中提取最大 ID
        // 2. 使用乐观锁更新游标表
        // 3. 插入游标事件
    }
}
```

### 10. 执行收尾器 (ExecutionFinalizer)

**职责**：聚合统计信息，判断最终状态，更新数据库。

**接口定义**：

```java
public interface ExecutionFinalizer {
    /**
     * 完成执行
     * @param context 收尾上下文
     * @return 收尾结果
     */
    FinalizationResult finalize(FinalizationContext context);
}
```

## Data Models

### 1. 领域模型

#### TaskAggregate（任务聚合根）

```java
public class TaskAggregate {
    private Long id;
    private String idempotentKey;
    private TaskStatus status;
    private String leaseOwner;
    private Instant leasedUntil;
    private ProvenanceConfig provenanceConfigSnapshot;
    private ExprSnapshot exprSnapshot;
    private String configHash;
    private OperationType operationType;
    private Integer retryCount;
    private String errorCode;
    private Instant createdAt;
    private Instant updatedAt;
    
    // 业务方法
    public void acquireLease(String owner, Duration duration);
    public void renewLease(Duration duration);
    public void releaseLease();
    public void markAsRunning();
    public void markAsSucceeded();
    public void markAsFailed(String errorCode);
}
```

#### TaskRunAggregate（执行记录聚合根）

```java
public class TaskRunAggregate {
    private Long id;
    private Long taskId;
    private Integer attemptNo;
    private TaskRunStatus status;
    private Instant windowStart;
    private Instant windowEnd;
    private String correlationId;
    private String schedulerRunId;
    private JsonNode stats;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    
    // 业务方法
    public void start();
    public void complete(ExecutionStats stats);
    public void fail(String errorMessage);
}
```


#### RunBatchAggregate（批次聚合根）

```java
public class RunBatchAggregate {
    private Long id;
    private Long runId;
    private Integer batchNo;
    private String idempotentKey;
    private BatchStatus status;
    private JsonNode batchParams;
    private JsonNode stats;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    
    // 业务方法
    public void start();
    public void complete(BatchStats stats);
    public void fail(String errorMessage);
    public boolean isCompleted();
}
```

#### CursorAggregate（游标聚合根）

```java
public class CursorAggregate {
    private Long id;
    private String provenanceCode;
    private String cursorKey;
    private String cursorValue;
    private Instant normalizedInstant;
    private Integer version;
    private Instant updatedAt;
    
    // 业务方法
    public void advance(String newValue, Instant newInstant);
    public void validateVersion(Integer expectedVersion);
}
```

### 2. 值对象

#### LeaseOwner（租约持有者）

```java
public record LeaseOwner(
    String nodeId,
    String randomId
) {
    public static LeaseOwner generate(String nodeId) {
        return new LeaseOwner(nodeId, UUID.randomUUID().toString());
    }
    
    @Override
    public String toString() {
        return nodeId + ":" + randomId;
    }
}
```

#### ExecutionWindow（执行窗口）

```java
public record ExecutionWindow(
    Instant start,
    Instant end
) {
    public void validate() {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("窗口起始时间不能晚于结束时间");
        }
    }
}
```

#### BatchParams（批次参数）

```java
public record BatchParams(
    Integer retstart,      // PubMed 分页起始位置
    Integer retmax,        // PubMed 分页大小
    String webEnv,         // PubMed WebEnv
    String cursorToken,    // EPMC cursor token
    Instant timeStart,     // 时间范围起始
    Instant timeEnd        // 时间范围结束
) {}
```

#### BatchStats（批次统计信息）

```java
public record BatchStats(
    Integer recordCount,
    Long fileSizeBytes,
    String storagePath,
    Instant maxTimestamp,
    Long maxRecordId,
    Duration executionDuration
) {}
```

#### ExecutionStats（执行统计信息）

```java
public record ExecutionStats(
    Integer totalBatches,
    Integer succeededBatches,
    Integer failedBatches,
    Integer totalRecords,
    Long totalFileSizeBytes,
    Duration totalDuration
) {
    public TaskStatus determineStatus() {
        if (failedBatches == 0) {
            return TaskStatus.SUCCEEDED;
        } else if (succeededBatches == 0) {
            return TaskStatus.FAILED;
        } else {
            return TaskStatus.PARTIAL;
        }
    }
}
```

### 3. 领域事件

```java
// 任务开始事件
public record TaskStartedEvent(
    Long taskId,
    Long runId,
    String correlationId,
    Instant timestamp
) implements DomainEvent {}

// 批次完成事件
public record BatchCompletedEvent(
    Long batchId,
    Long runId,
    Integer batchNo,
    BatchStatus status,
    BatchStats stats,
    Instant timestamp
) implements DomainEvent {}

// 游标推进事件
public record CursorAdvancedEvent(
    Long cursorId,
    String provenanceCode,
    String oldValue,
    String newValue,
    Long runId,
    Instant timestamp
) implements DomainEvent {}

// 任务完成事件
public record TaskCompletedEvent(
    Long taskId,
    Long runId,
    TaskStatus status,
    ExecutionStats stats,
    Instant timestamp
) implements DomainEvent {}
```

### 4. 数据库表结构

#### ing_task（任务表）

```sql
CREATE TABLE ing_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotent_key VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    lease_owner VARCHAR(128),
    leased_until DATETIME(6),
    provenance_config_snapshot JSON NOT NULL,
    expr_snapshot JSON NOT NULL,
    config_hash VARCHAR(64) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    retry_count INT DEFAULT 0,
    error_code VARCHAR(50),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_status (status),
    INDEX idx_lease (lease_owner, leased_until)
);
```

#### ing_task_run（执行记录表）

```sql
CREATE TABLE ing_task_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    window_start DATETIME(6),
    window_end DATETIME(6),
    correlation_id VARCHAR(64),
    scheduler_run_id VARCHAR(64),
    stats JSON,
    error_message TEXT,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    INDEX idx_task_id (task_id),
    INDEX idx_correlation_id (correlation_id)
);
```

#### ing_task_run_batch（批次表）

```sql
CREATE TABLE ing_task_run_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    batch_no INT NOT NULL,
    idempotent_key VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    batch_params JSON NOT NULL,
    stats JSON,
    error_message TEXT,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    INDEX idx_run_id (run_id),
    INDEX idx_status (status)
);
```

#### ing_cursor（游标表）

```sql
CREATE TABLE ing_cursor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provenance_code VARCHAR(50) NOT NULL,
    cursor_key VARCHAR(100) NOT NULL,
    cursor_value VARCHAR(255) NOT NULL,
    normalized_instant DATETIME(6),
    version INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_provenance_key (provenance_code, cursor_key)
);
```

#### ing_cursor_event（游标事件表）

```sql
CREATE TABLE ing_cursor_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cursor_id BIGINT NOT NULL,
    idempotent_key VARCHAR(64) NOT NULL UNIQUE,
    operation_type VARCHAR(20) NOT NULL,
    old_value VARCHAR(255),
    new_value VARCHAR(255) NOT NULL,
    run_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_cursor_id (cursor_id),
    INDEX idx_run_id (run_id)
);
```

#### outbox（消息发件箱表）

```sql
CREATE TABLE outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel VARCHAR(100) NOT NULL,
    dedup_key VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    UNIQUE KEY uk_channel_dedup (channel, dedup_key),
    INDEX idx_status (status)
);
```

## Error Handling

### 异常层次结构

```java
// 基础异常
public class TaskExecutionException extends ApplicationException {
    public TaskExecutionException(String message) {
        super(message);
    }
}

// 租约获取失败异常
public class LeaseAcquisitionFailedException extends TaskExecutionException {
    public LeaseAcquisitionFailedException(Long taskId, String reason) {
        super(String.format("租约获取失败: taskId=%d, reason=%s", taskId, reason));
    }
}

// 配置篡改异常
public class ConfigurationTamperedException extends TaskExecutionException {
    public ConfigurationTamperedException(String message) {
        super(message);
    }
}

// 表达式编译异常
public class ExpressionCompilationException extends TaskExecutionException {
    public ExpressionCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 批次规划异常
public class BatchPlanningException extends TaskExecutionException {
    public BatchPlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 批次执行异常
public class BatchExecutionException extends TaskExecutionException {
    public BatchExecutionException(Integer batchNo, String message, Throwable cause) {
        super(String.format("批次执行失败: batchNo=%d, message=%s", batchNo, message), cause);
    }
}
```

### 错误处理策略

| 异常类型 | 处理策略 | 任务状态 | 是否重试 |
|---------|---------|---------|---------|
| LeaseAcquisitionFailedException | 优雅退出，记录 INFO 日志 | 不变 | 否 |
| ConfigurationTamperedException | 标记任务 FAILED，记录 ERROR 日志 | FAILED | 否 |
| ExpressionCompilationException | 标记任务 FAILED，记录 ERROR 日志 | FAILED | 否 |
| BatchPlanningException | 标记任务 FAILED，记录 ERROR 日志 | FAILED | 是（MQ 重试） |
| BatchExecutionException | 标记批次 FAILED，继续执行其他批次 | 部分失败 | 是（批次级重试） |
| DatabaseException | 抛出异常，触发 MQ 重试 | 不变 | 是（MQ 重试） |
| MinIOException | 重试 3 次，仍失败则标记批次 FAILED | 部分失败 | 是（批次级重试） |

### 错误码定义

```java
public enum TaskExecutionErrorCode {
    ING_1001("ING-1001", "租约获取失败"),
    ING_1002("ING-1002", "配置哈希校验失败"),
    ING_1003("ING-1003", "表达式编译失败"),
    ING_1004("ING-1004", "批次规划失败"),
    ING_1005("ING-1005", "批次执行失败"),
    ING_1006("ING-1006", "游标推进失败"),
    ING_1007("ING-1007", "对象存储上传失败"),
    ING_1008("ING-1008", "数据库操作失败");
    
    private final String code;
    private final String message;
}
```


## Testing Strategy

### 1. 单元测试

#### Domain Layer 测试

**测试目标**：验证聚合根的业务不变性和领域逻辑

```java
// TaskAggregate 测试
@Test
void shouldAcquireLease_whenLeaseIsAvailable() {
    TaskAggregate task = new TaskAggregate();
    LeaseOwner owner = LeaseOwner.generate("node-1");
    
    task.acquireLease(owner.toString(), Duration.ofSeconds(60));
    
    assertThat(task.getLeaseOwner()).isEqualTo(owner.toString());
    assertThat(task.getLeasedUntil()).isAfter(Instant.now());
}

@Test
void shouldThrowException_whenAcquiringExpiredLease() {
    TaskAggregate task = new TaskAggregate();
    task.setLeasedUntil(Instant.now().minusSeconds(10));
    
    assertThatThrownBy(() -> task.acquireLease("node-2", Duration.ofSeconds(60)))
        .isInstanceOf(LeaseExpiredException.class);
}

// ExecutionStats 测试
@Test
void shouldDetermineStatusAsSucceeded_whenAllBatchesSucceeded() {
    ExecutionStats stats = new ExecutionStats(10, 10, 0, 5000, 1024000L, Duration.ofMinutes(5));
    
    assertThat(stats.determineStatus()).isEqualTo(TaskStatus.SUCCEEDED);
}

@Test
void shouldDetermineStatusAsPartial_whenSomeBatchesFailed() {
    ExecutionStats stats = new ExecutionStats(10, 7, 3, 3500, 716800L, Duration.ofMinutes(5));
    
    assertThat(stats.determineStatus()).isEqualTo(TaskStatus.PARTIAL);
}
```

#### Application Layer 测试

**测试目标**：验证用例编排逻辑和事务边界

```java
@Test
void shouldExecuteTaskSuccessfully_whenAllStepsSucceed() {
    // Given
    TaskExecutionCommand command = new TaskExecutionCommand(taskId, idempotentKey);
    when(idempotencyChecker.isAlreadyCompleted(taskId, idempotentKey)).thenReturn(false);
    when(leaseAcquisitionService.tryAcquire(any(), any(), anyInt())).thenReturn(LeaseAcquisitionResult.success());
    
    // When
    TaskExecutionResult result = orchestrator.execute(command);
    
    // Then
    assertThat(result.isSuccess()).isTrue();
    verify(executionFinalizer).finalize(any());
}

@Test
void shouldSkipExecution_whenTaskAlreadyCompleted() {
    // Given
    TaskExecutionCommand command = new TaskExecutionCommand(taskId, idempotentKey);
    when(idempotencyChecker.isAlreadyCompleted(taskId, idempotentKey)).thenReturn(true);
    
    // When
    TaskExecutionResult result = orchestrator.execute(command);
    
    // Then
    assertThat(result.isSkipped()).isTrue();
    verify(leaseAcquisitionService, never()).tryAcquire(any(), any(), anyInt());
}
```

#### Infrastructure Layer 测试

**测试目标**：验证仓储实现和外部集成

```java
@Test
void shouldAcquireLeaseSuccessfully_whenLeaseIsAvailable() {
    // Given
    Long taskId = 1L;
    String leaseOwner = "node-1:uuid";
    
    // When
    int affectedRows = taskMapper.tryAcquireLease(taskId, leaseOwner, Instant.now().plusSeconds(60));
    
    // Then
    assertThat(affectedRows).isEqualTo(1);
}

@Test
void shouldUploadToMinIO_andReturnStoragePath() {
    // Given
    byte[] compressedData = gzipCompress(rawData);
    String expectedPath = "papertrace-ingest/pubmed/2024/01/run_12345/batch_001.json.gz";
    
    // When
    String actualPath = minioAdapter.upload(compressedData, expectedPath);
    
    // Then
    assertThat(actualPath).isEqualTo(expectedPath);
}
```

### 2. 集成测试

#### 端到端流程测试

```java
@SpringBootTest
@Testcontainers
class TaskExecutionIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    
    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest");
    
    @Autowired
    private TaskExecutionOrchestrator orchestrator;
    
    @Test
    void shouldExecuteTaskEndToEnd_withPubMedDataSource() {
        // Given: 准备任务数据
        TaskAggregate task = createPubMedTask();
        taskRepository.save(task);
        
        // When: 执行任务
        TaskExecutionCommand command = new TaskExecutionCommand(task.getId(), task.getIdempotentKey());
        TaskExecutionResult result = orchestrator.execute(command);
        
        // Then: 验证结果
        assertThat(result.isSuccess()).isTrue();
        
        // 验证任务状态
        TaskAggregate updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        
        // 验证批次记录
        List<RunBatchAggregate> batches = batchRepository.findByRunId(result.getRunId());
        assertThat(batches).allMatch(b -> b.getStatus() == BatchStatus.SUCCEEDED);
        
        // 验证游标推进
        CursorAggregate cursor = cursorRepository.findByProvenanceCode("PUBMED").orElseThrow();
        assertThat(cursor.getCursorValue()).isNotNull();
        
        // 验证 Outbox 消息
        List<OutboxMessage> messages = outboxRepository.findByChannel("ingest.data.pubmed");
        assertThat(messages).hasSize(batches.size());
    }
    
    @Test
    void shouldHandlePartialFailure_andContinueExecution() {
        // Given: 准备任务数据，模拟部分批次失败
        TaskAggregate task = createTaskWithFailingBatches();
        taskRepository.save(task);
        
        // When: 执行任务
        TaskExecutionResult result = orchestrator.execute(command);
        
        // Then: 验证部分成功
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PARTIAL);
        
        List<RunBatchAggregate> batches = batchRepository.findByRunId(result.getRunId());
        assertThat(batches).anyMatch(b -> b.getStatus() == BatchStatus.SUCCEEDED);
        assertThat(batches).anyMatch(b -> b.getStatus() == BatchStatus.FAILED);
    }
}
```

### 3. 性能测试

```java
@Test
void shouldHandleConcurrentTaskExecution_withoutLeaseConflict() throws Exception {
    // Given: 准备 10 个任务
    List<TaskAggregate> tasks = IntStream.range(0, 10)
        .mapToObj(i -> createTask())
        .collect(Collectors.toList());
    tasks.forEach(taskRepository::save);
    
    // When: 并发执行
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<Future<TaskExecutionResult>> futures = tasks.stream()
        .map(task -> executor.submit(() -> orchestrator.execute(
            new TaskExecutionCommand(task.getId(), task.getIdempotentKey()))))
        .collect(Collectors.toList());
    
    // Then: 验证所有任务都成功执行
    List<TaskExecutionResult> results = futures.stream()
        .map(f -> {
            try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        })
        .collect(Collectors.toList());
    
    assertThat(results).allMatch(TaskExecutionResult::isSuccess);
}
```

## Configuration Management

### 1. 应用配置

```yaml
# application.yaml
patra:
  ingest:
    task-execution:
      # 租约配置
      lease:
        duration-seconds: 60
        renewal-interval-seconds: 20
      
      # 批次配置
      batch:
        max-batches-per-execution: 100
        default-page-size: 500
        execution-mode: SEQUENTIAL  # SEQUENTIAL | PARALLEL
        parallel-threads: 5
      
      # 重试配置
      retry:
        max-attempts: 3
        backoff-multiplier: 2
        initial-interval-ms: 1000
      
      # 心跳配置
      heartbeat:
        thread-pool-size: 10
      
      # 对象存储配置
      storage:
        bucket: papertrace-ingest
        compression-enabled: true
        upload-retry-times: 3

# 日志配置
logging:
  level:
    com.patra.ingest.app.usecase.taskexecution: INFO
    com.patra.ingest.domain: DEBUG
    com.patra.ingest.infra: INFO
```

### 2. 数据源配置快照示例

```json
{
  "provenanceCode": "PUBMED",
  "baseUrl": "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
  "timeout": {
    "connectTimeoutMs": 5000,
    "readTimeoutMs": 30000
  },
  "retry": {
    "maxAttempts": 3,
    "backoffMultiplier": 2,
    "initialIntervalMs": 1000
  },
  "pagination": {
    "mode": "PAGE_NUMBER",
    "pageSize": 500
  },
  "batch": {
    "strategy": "PAGINATED",
    "maxBatchesPerExecution": 10
  }
}
```

### 3. 表达式快照示例

```json
{
  "exprType": "SEARCH",
  "conditions": [
    {
      "field": "keyword",
      "operator": "CONTAINS",
      "value": "cancer"
    },
    {
      "field": "publicationDate",
      "operator": "BETWEEN",
      "value": ["2024-01-01", "2024-01-31"]
    }
  ],
  "sort": {
    "field": "publicationDate",
    "order": "ASC"
  },
  "exprHash": "sha256:abc123..."
}
```

## Observability

### 1. 日志规范

#### 日志前缀

```
[INGEST][APP] - Application Layer
[INGEST][DOMAIN] - Domain Layer
[INGEST][INFRA] - Infrastructure Layer
[INGEST][ADAPTER] - Adapter Layer
```

#### 关键日志点

```java
// 步骤 0: 幂等检查
log.info("[INGEST][APP] Task idempotency check: taskId={} idempotentKey={} alreadyCompleted={}", 
    taskId, idempotentKey, isCompleted);

// 步骤 1: 租约抢占
log.info("[INGEST][APP] Lease acquisition attempt: taskId={} leaseOwner={}", taskId, leaseOwner);
log.info("[INGEST][APP] Lease acquisition result: taskId={} success={} reason={}", 
    taskId, result.isSuccess(), result.getReason());

// 步骤 2: 会话初始化
log.info("[INGEST][APP] Execution session initialized: taskId={} runId={} attemptNo={} correlationId={}", 
    taskId, runId, attemptNo, correlationId);

// 步骤 3: 配置还原
log.info("[INGEST][APP] Config snapshot restored: taskId={} provenanceCode={} configHash={}", 
    taskId, provenanceCode, configHash);

// 步骤 4: 表达式编译
log.info("[INGEST][APP] Expression compiled: taskId={} provenanceCode={} exprType={}", 
    taskId, provenanceCode, exprType);

// 步骤 5: 批次规划
log.info("[INGEST][APP] Batch plan generated: runId={} totalBatches={} totalRecords={}", 
    runId, batchCount, totalRecords);

// 步骤 6: 批次执行
log.info("[INGEST][APP] Batch execution started: runId={} batchNo={} batchParams={}", 
    runId, batchNo, batchParams);
log.info("[INGEST][APP] Batch execution completed: runId={} batchNo={} status={} recordCount={} fileSizeBytes={} durationMs={}", 
    runId, batchNo, status, recordCount, fileSizeBytes, durationMs);

// 步骤 7: 游标推进
log.info("[INGEST][APP] Cursor advancement: cursorId={} oldValue={} newValue={} runId={}", 
    cursorId, oldValue, newValue, runId);

// 步骤 8: 状态更新
log.info("[INGEST][APP] Task execution finalized: taskId={} runId={} status={} stats={}", 
    taskId, runId, status, stats);

// 心跳续租
log.debug("[INGEST][APP] Heartbeat renewal: taskId={} leaseOwner={} newLeasedUntil={}", 
    taskId, leaseOwner, newLeasedUntil);

// 异常日志
log.error("[INGEST][APP] Task execution failed: taskId={} runId={} errorCode={} errorMessage={}", 
    taskId, runId, errorCode, errorMessage, exception);
```

### 2. 指标收集

```java
// 任务执行指标
@Timed(value = "ingest.task.execution.duration", description = "任务执行耗时")
@Counted(value = "ingest.task.execution.total", description = "任务执行总数")
public TaskExecutionResult execute(TaskExecutionCommand command) {
    // ...
}

// 批次执行指标
meterRegistry.timer("ingest.batch.execution.duration",
    Tags.of("provenance", provenanceCode, "status", status))
    .record(duration);

meterRegistry.counter("ingest.batch.execution.records",
    Tags.of("provenance", provenanceCode))
    .increment(recordCount);

// 租约抢占指标
meterRegistry.counter("ingest.lease.acquisition",
    Tags.of("result", result.isSuccess() ? "success" : "failed"))
    .increment();

// 游标推进指标
meterRegistry.counter("ingest.cursor.advancement",
    Tags.of("provenance", provenanceCode, "operation", operationType))
    .increment();
```

### 3. 追踪链路

```java
// 在 Adapter 层注入 traceId
@Component
public class TaskReadyMessageConsumer {
    
    @RocketMQMessageListener(topic = "ingest.task.ready", consumerGroup = "ingest-executor")
    public void onMessage(TaskReadyMessage message) {
        // 提取或生成 correlationId
        String correlationId = message.getCorrelationId() != null 
            ? message.getCorrelationId() 
            : UUID.randomUUID().toString();
        
        // 设置 MDC
        MDC.put("correlationId", correlationId);
        MDC.put("taskId", message.getTaskId().toString());
        
        try {
            orchestrator.execute(new TaskExecutionCommand(
                message.getTaskId(), 
                message.getIdempotentKey(),
                correlationId
            ));
        } finally {
            MDC.clear();
        }
    }
}
```

## Deployment Considerations

### 1. 资源需求

- **CPU**: 2-4 核（根据并发批次数调整）
- **内存**: 4-8 GB（根据批次大小和并发数调整）
- **数据库连接池**: 20-50 连接
- **线程池**: 
  - 心跳续租线程池: 10 线程
  - 批次执行线程池（并行模式）: 5-10 线程

### 2. 扩展性

- **水平扩展**: 支持多节点部署，通过租约机制自动负载均衡
- **垂直扩展**: 增加单节点资源，提高批次并发度
- **数据库扩展**: 考虑分库分表（按 provenance_code 或时间范围）

### 3. 监控告警

- **任务执行失败率** > 10%：触发告警
- **批次执行失败率** > 20%：触发告警
- **租约抢占失败率** > 50%：触发告警（可能节点过多）
- **游标推进失败**：立即告警（影响增量采集）
- **心跳续租失败**：触发告警（节点可能异常）

### 4. 运维建议

- **定期清理历史数据**: 保留最近 30 天的执行记录和批次记录
- **监控对象存储空间**: 设置存储配额和清理策略
- **数据库索引优化**: 定期分析慢查询并优化索引
- **租约超时调优**: 根据任务平均执行时间调整租约时长

## Design Decisions and Trade-offs

### 1. 为什么使用数据库 CAS 而不是 ZooKeeper/etcd？

**决策**: 使用数据库的 CAS 机制实现分布式租约

**理由**:
- 减少基础设施依赖，降低运维复杂度
- 数据库已经是必需组件，无需引入额外中间件
- CAS 机制足够满足租约抢占需求
- 租约超时自动释放，无需担心死锁

**权衡**:
- 数据库压力增加（心跳续租频繁更新）
- 租约精度受数据库性能影响
- 不支持分布式锁的高级特性（如公平锁、读写锁）

### 2. 为什么批次预先落库而不是动态生成？

**决策**: 在批次规划阶段预先生成所有批次记录并落库

**理由**:
- 支持断点续传：任务重启后可以从 PENDING 批次继续
- 进度可视化：可以实时查看批次执行进度
- 幂等保障：每批有独立的幂等键，避免重复执行
- 并发执行：可以并发执行多个批次

**权衡**:
- 数据库写入压力增加（大任务可能生成数百个批次）
- 批次规划失败会留下孤儿记录（需要定期清理）

### 3. 为什么游标推进在所有批次完成后而不是每批完成后？

**决策**: 游标推进在所有批次执行完成后统一进行

**理由**:
- 避免部分失败污染游标：如果某批失败，游标不应推进
- 保证数据完整性：确保游标值对应的数据已全部采集
- 简化逻辑：不需要处理并发批次的游标竞争

**权衡**:
- 任务失败时游标不推进，下次会重复采集部分数据
- 长时间运行的任务，游标更新延迟较大

### 4. 为什么使用 Outbox 模式而不是直接发送 MQ 消息？

**决策**: 使用 Outbox 模式，在事务内写入消息，异步发布

**理由**:
- 保证事务一致性：批次状态更新和消息发送在同一事务
- 避免消息丢失：即使 MQ 不可用，消息也已持久化
- 支持重试：Outbox 轮询器可以重试失败的消息

**权衡**:
- 消息延迟增加（需要等待 Outbox 轮询器）
- 数据库压力增加（Outbox 表写入和轮询）

### 5. 为什么支持部分失败容忍？

**决策**: 部分批次失败不影响其他批次执行，任务状态为 PARTIAL

**理由**:
- 提高采集成功率：部分数据总比没有数据好
- 减少重试成本：不需要重新执行已成功的批次
- 支持人工介入：可以针对失败批次单独处理

**权衡**:
- 增加状态复杂度（SUCCEEDED / FAILED / PARTIAL）
- 需要额外的失败批次处理流程

