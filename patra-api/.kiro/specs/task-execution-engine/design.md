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

    /**
     * 验证租约仍然有效（心跳失败后主动检查）
     * @param taskId 任务 ID
     * @param expectedOwner 期望的租约持有者
     * @return 验证结果
     * @throws LeaseRevokedException 如果租约已被其他节点接管
     */
    LeaseValidationResult validateLease(Long taskId, String expectedOwner) throws LeaseRevokedException;
}

/**
 * 租约验证结果
 */
public record LeaseValidationResult(
    boolean isValid,
    String currentOwner,
    Instant leasedUntil
) {}

/**
 * 租约被接管异常（Critical 级别，需快速失败）
 */
public class LeaseRevokedException extends RuntimeException {
    private final Long taskId;
    private final String expectedOwner;
    private final String actualOwner;

    public LeaseRevokedException(Long taskId, String expectedOwner, String actualOwner) {
        super(String.format("租约已被接管: taskId=%d, expectedOwner=%s, actualOwner=%s",
                taskId, expectedOwner, actualOwner));
        this.taskId = taskId;
        this.expectedOwner = expectedOwner;
        this.actualOwner = actualOwner;
    }
}

/**
 * 心跳续租实现示例（包含失败容忍与主动验证）
 */
@Service
public class HeartbeatRenewalServiceImpl implements HeartbeatRenewalService {
    private final TaskRepository taskRepository;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService heartbeatExecutor;

    @Override
    public ScheduledFuture<?> startHeartbeat(Long taskId, String leaseOwner, int renewalInterval) {
        AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final int MAX_CONSECUTIVE_FAILURES = 3; // 连续失败阈值

        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                // 尝试续租
                boolean renewed = taskRepository.renewLease(taskId, leaseOwner, Duration.ofSeconds(60));

                if (renewed) {
                    consecutiveFailures.set(0); // 成功，重置计数器
                    log.debug("[INGEST][INFRA] Heartbeat renewed: taskId={} owner={}", taskId, leaseOwner);
                } else {
                    int failures = consecutiveFailures.incrementAndGet();
                    log.warn("[INGEST][INFRA] Heartbeat renewal failed: taskId={} owner={} failures={}",
                            taskId, leaseOwner, failures);

                    // 达到失败阈值，主动验证租约
                    if (failures >= MAX_CONSECUTIVE_FAILURES) {
                        validateLease(taskId, leaseOwner); // 如果租约被接管，会抛出 LeaseRevokedException
                    }
                }
            } catch (Exception e) {
                int failures = consecutiveFailures.incrementAndGet();
                log.error("[INGEST][INFRA] Heartbeat renewal error: taskId={} owner={} failures={}",
                        taskId, leaseOwner, failures, e);

                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    try {
                        validateLease(taskId, leaseOwner);
                    } catch (LeaseRevokedException ex) {
                        // 租约被接管，快速失败（由上层捕获并终止任务）
                        throw ex;
                    }
                }
            }
        }, renewalInterval, renewalInterval, TimeUnit.SECONDS);
    }

    @Override
    public LeaseValidationResult validateLease(Long taskId, String expectedOwner) throws LeaseRevokedException {
        TaskAggregate task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        String currentOwner = task.getLeaseOwner();

        if (!expectedOwner.equals(currentOwner)) {
            // 租约已被其他节点接管
            meterRegistry.counter("ingest.lease.revoked",
                    Tags.of("taskId", String.valueOf(taskId)))
                    .increment();

            throw new LeaseRevokedException(taskId, expectedOwner, currentOwner);
        }

        return new LeaseValidationResult(true, currentOwner, task.getLeasedUntil());
    }
}

/**
 * 批量心跳续租服务（性能优化 - 解决 Critical-4）
 */
@Service
@ConditionalOnProperty(name = "patra.ingest.task-execution.heartbeat.batch-renewal-enabled", havingValue = "true")
public class BatchHeartbeatRenewalService {
    private final TaskRepository taskRepository;
    private final MeterRegistry meterRegistry;

    @Value("${patra.ingest.task-execution.heartbeat.batch-renewal-size:50}")
    private int batchSize;

    /**
     * 批量续租当前节点的所有任务
     *
     * @param taskIds 任务 ID 列表
     * @param leaseOwner 租约持有者
     * @param leaseDuration 租约时长
     * @return 续租成功的任务数
     */
    public int batchRenewLeases(List<Long> taskIds, String leaseOwner, Duration leaseDuration) {
        if (taskIds.isEmpty()) {
            return 0;
        }

        // 单次批量续租限制（避免 SQL 过长）
        List<List<Long>> batches = Lists.partition(taskIds, batchSize);
        int totalRenewed = 0;

        for (List<Long> batch : batches) {
            int renewed = taskRepository.batchRenewLeases(batch, leaseOwner, leaseDuration);
            totalRenewed += renewed;

            meterRegistry.counter("ingest.heartbeat.batch.renewal",
                    Tags.of("batch_size", String.valueOf(batch.size()),
                            "renewed", String.valueOf(renewed)))
                    .increment();
        }

        return totalRenewed;
    }
}

/**
 * TaskRepository 批量续租接口（Infrastructure 层实现）
 */
public interface TaskRepository {
    // ... 其他方法

    /**
     * 批量续租租约（单条 SQL 更新多个任务）
     *
     * @param taskIds 任务 ID 列表
     * @param leaseOwner 租约持有者
     * @param leaseDuration 租约时长
     * @return 更新的行数
     */
    int batchRenewLeases(List<Long> taskIds, String leaseOwner, Duration leaseDuration);
}

/**
 * MyBatis 批量续租 SQL 示例
 *
 * UPDATE ing_task
 * SET leased_until = NOW() + INTERVAL ? SECOND
 * WHERE lease_owner = ?
 *   AND id IN
 *   <foreach collection="taskIds" item="id" open="(" separator="," close=")">
 *     #{id}
 *   </foreach>
 */
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

// PubMed 实现（包含批次数限制和分批插入 - 解决 Critical-5）
@Component
public class PubMedBatchPlanner implements BatchPlanner {
    private final BatchRepository batchRepository;

    @Value("${patra.ingest.task-execution.batch.max-batches-per-execution:1000}")
    private int maxBatchesPerExecution;

    @Value("${patra.ingest.task-execution.batch.insert-chunk-size:100}")
    private int insertChunkSize; // 批次记录分批插入的大小

    @Override
    public BatchPlan plan(BatchPlanningContext context) {
        // 1. 调用 ESearch API 获取总数和 WebEnv
        ESearchResult searchResult = pubMedClient.search(context.getQueryParams());
        int totalRecords = searchResult.getTotalCount();
        String webEnv = searchResult.getWebEnv();

        // 2. 计算批次数，增加上限检查
        int pageSize = context.getPageSize();
        int calculatedBatches = (int) Math.ceil((double) totalRecords / pageSize);
        int actualBatches = Math.min(calculatedBatches, maxBatchesPerExecution);

        // 3. 检查批次数是否超过限制
        if (calculatedBatches > maxBatchesPerExecution) {
            log.warn("[INGEST][APP] Batch count exceeds limit: calculated={} limit={} taskId={}",
                    calculatedBatches, maxBatchesPerExecution, context.getTaskId());
            // 可以选择：1) 拒绝任务 2) 拆分为多个子任务 3) 只执行前 N 批（当前采用）
        }

        // 4. 生成批次记录
        List<RunBatchAggregate> batches = new ArrayList<>(actualBatches);
        for (int i = 0; i < actualBatches; i++) {
            batches.add(createBatch(
                    context.getRunId(),
                    i + 1,
                    i * pageSize,
                    pageSize,
                    webEnv
            ));
        }

        // 5. 分批插入数据库（避免单次插入过多）
        List<List<RunBatchAggregate>> chunks = Lists.partition(batches, insertChunkSize);
        for (List<RunBatchAggregate> chunk : chunks) {
            batchRepository.batchInsert(chunk);
            log.debug("[INGEST][APP] Batch records inserted: count={} runId={}",
                    chunk.size(), context.getRunId());
        }

        return new BatchPlan(actualBatches, totalRecords, webEnv);
    }

    private RunBatchAggregate createBatch(Long runId, int batchNo, int retstart, int retmax, String webEnv) {
        return RunBatchAggregate.builder()
                .runId(runId)
                .batchNo(batchNo)
                .idempotentKey(generateIdempotentKey(runId, batchNo))
                .status(BatchStatus.PENDING)
                .batchParams(buildBatchParams(retstart, retmax, webEnv))
                .build();
    }
}

// EPMC 实现
@Component
public class EpmcBatchPlanner implements BatchPlanner {
    @Override
    public BatchPlan plan(BatchPlanningContext context) {
        // 1. 调用初始 Search API 获取第一页和 cursor
        // 2. 生成批次记录（每批使用上一批的 cursor）
        // 3. 同样实施批次数限制和分批插入
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

#### 游标推进重试服务 (CursorAdvancementRetryService)

**职责**：异步重试 CURSOR_PENDING 状态任务的游标推进，确保最终一致性。

**接口定义**：

```java
public interface CursorAdvancementRetryService {
    /**
     * 重试游标推进
     * @param taskId 任务 ID
     * @param runId 执行记录 ID
     * @return 重试结果
     */
    CursorRetryResult retryAdvancement(Long taskId, Long runId);

    /**
     * 扫描并重试所有 CURSOR_PENDING 任务
     * @return 重试统计
     */
    CursorRetryStats scanAndRetry();
}

@Service
public class CursorAdvancementRetryServiceImpl implements CursorAdvancementRetryService {
    private final TaskRepository taskRepository;
    private final CursorAdvancerRegistry cursorAdvancerRegistry;

    @Override
    public CursorRetryResult retryAdvancement(Long taskId, Long runId) {
        // 1. 查询任务和执行记录，验证状态为 CURSOR_PENDING
        // 2. 重新调用 CursorAdvancer.advance()
        // 3. IF 成功 THEN 更新任务状态为 SUCCEEDED
        // 4. IF 失败 THEN 记录重试次数，超过阈值（如 5 次）则告警
        // 5. 使用幂等键和 lastAttemptRunId 防止重复推进
    }

    @Scheduled(fixedDelay = 60000) // 每 60 秒扫描一次
    @Override
    public CursorRetryStats scanAndRetry() {
        // 1. 查询所有 CURSOR_PENDING 状态的任务（限制 100 条）
        // 2. 按创建时间排序，优先处理旧任务
        // 3. 对每个任务调用 retryAdvancement()
        // 4. 聚合统计信息（成功数、失败数、跳过数）
    }
}

/**
 * 游标重试结果
 */
public record CursorRetryResult(
    boolean success,
    String reason,
    int retryCount
) {}

/**
 * 游标重试统计
 */
public record CursorRetryStats(
    int totalScanned,
    int successCount,
    int failedCount,
    int skippedCount
) {}
```

**重试策略**：
- 扫描频率：每 60 秒
- 最大重试次数：5 次
- 失败处理：超过 5 次后发送 Critical 告警，需人工介入
- 幂等保障：通过 `lastAttemptRunId` 和游标事件幂等键防止重复推进

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

    // 上游关联（调度层）
    private Long scheduleInstanceId;  // 调度实例 ID
    private Long planId;              // 计划 ID
    private Long sliceId;             // 切片 ID

    // 任务标识与配置
    private String provenanceCode;    // 来源代码（PUBMED/EPMC/CROSSREF）
    private String operationCode;     // 操作类型（HARVEST/BACKFILL/UPDATE/METRICS）
    private JsonNode params;          // 任务参数（规范化 JSON）
    private String idempotentKey;     // 幂等键（SHA256）
    private String exprHash;          // 表达式哈希（冗余，来自 Slice）
    private Integer priority;         // 优先级（1高→9低）

    // 租约管理
    private String leaseOwner;        // 租约持有者（nodeId:randomId）
    private Instant leasedUntil;      // 租约到期时间
    private Integer leaseCount;       // 累计抢占/续租次数
    private Instant lastHeartbeatAt;  // 最近心跳时间

    // 状态与重试
    private TaskStatus status;        // 任务状态
    private Integer retryCount;       // 重试次数
    private String lastErrorCode;     // 最近错误码
    private String lastErrorMsg;      // 最近错误信息

    // 时间线
    private Instant scheduledAt;      // 计划开始时间
    private Instant startedAt;        // 实际开始时间
    private Instant finishedAt;       // 结束时间

    // 追踪
    private String schedulerRunId;    // 外部调度运行 ID
    private String correlationId;     // 追踪/关联 ID

    // 审计字段
    private Long version;             // 乐观锁版本号
    private Instant createdAt;
    private Instant updatedAt;

    // 业务方法
    public void acquireLease(String owner, Instant leasedUntil);
    public void renewLease(Instant newLeasedUntil);
    public void releaseLease();
    public void markAsRunning(Instant startedAt);
    public void markAsSucceeded(Instant finishedAt);
    public void markAsFailed(Instant finishedAt, String errorCode, String errorMsg);
    public void markAsCancelled(Instant finishedAt);
    public void markAsPartial(Instant finishedAt); // 部分批次成功
    public void markAsCursorPending(); // 批次成功但游标推进失败
}

/**
 * 任务状态枚举（对应 ing_task_status 字典）
 *
 * QUEUED: 等待执行（已入队未被消费）
 * RUNNING: 执行中（不可重复启动）
 * SUCCEEDED: 成功完成
 * FAILED: 执行失败（可触发补偿或重试策略）
 * CANCELLED: 被主动终止或条件不满足取消
 * PARTIAL: 部分批次成功（设计扩展，需添加到字典）
 * CURSOR_PENDING: 批次全部成功但游标推进失败（设计扩展，需添加到字典）
 */
public enum TaskStatus {
    QUEUED,          // 对应 SQL 的 'QUEUED'
    RUNNING,         // 对应 SQL 的 'RUNNING'
    SUCCEEDED,       // 对应 SQL 的 'SUCCEEDED'
    FAILED,          // 对应 SQL 的 'FAILED'
    CANCELLED,       // 对应 SQL 的 'CANCELLED'
    PARTIAL,         // 新增状态（需添加到字典）
    CURSOR_PENDING   // 新增状态（需添加到字典）
}
```

#### TaskRunAggregate（执行记录聚合根）

```java
public class TaskRunAggregate {
    private Long id;
    private Long taskId;
    private Integer attemptNo;

    // 冗余字段（来源于 task 链路，便于聚合统计）
    private String provenanceCode;
    private String operationCode;

    private TaskRunStatus status;
    private JsonNode checkpoint;      // 运行级检查点（如 nextHint/resumeToken）
    private JsonNode stats;           // 统计信息：fetched/upserted/failed/pages 等
    private String error;             // 失败原因

    // 时间型切片时冗余窗口
    private Instant windowFrom;       // 窗口起(UTC)[含]
    private Instant windowTo;         // 窗口止(UTC)[不含]

    private Instant startedAt;
    private Instant finishedAt;
    private Instant lastHeartbeat;

    private String schedulerRunId;
    private String correlationId;

    // 审计字段
    private Long version;             // 乐观锁版本号
    private Instant createdAt;
    private Instant updatedAt;

    // 业务方法
    public void start(Instant startedAt);
    public void complete(ExecutionStats stats, Instant finishedAt);
    public void fail(String error, Instant finishedAt);
    public void heartbeat(Instant heartbeatAt);
}

/**
 * 任务运行状态枚举（对应 ing_task_run_status 字典）
 *
 * PLANNED: 已规划（run 记录已创建）
 * RUNNING: 执行中
 * SUCCEEDED: 成功完成
 * FAILED: 执行失败
 * CANCELLED: 被取消
 */
public enum TaskRunStatus {
    PLANNED("PLANNED", "已规划"),
    RUNNING("RUNNING", "运行中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;
}
```


#### RunBatchAggregate（批次聚合根）

```java
public class RunBatchAggregate {
    private Long id;
    private Long runId;

    // 冗余字段（来源于 task/run 链路，便于回溯与聚合）
    private Long taskId;
    private Long sliceId;
    private Long planId;
    private String exprHash;
    private String provenanceCode;
    private String operationCode;

    private Integer batchNo;          // 批次序号（1起，连续）
    private Integer pageNo;           // 页码（offset/limit 分页）
    private Integer pageSize;         // 页大小

    // 令牌分页（cursorMark/retstart 等）
    private String beforeToken;       // 该批开始令牌/位置
    private String afterToken;        // 该批结束令牌/下一位置

    private String idempotentKey;     // 幂等键：SHA256(run_id + before_token | page_no)
    private Integer recordCount;      // 本批采集记录数
    private BatchStatus status;
    private Instant committedAt;      // 提交时间（成功/失败时间）
    private String error;             // 失败原因
    private JsonNode stats;           // 批次统计信息（JSON）

    // 审计字段
    private Long version;             // 乐观锁版本号
    private Instant createdAt;
    private Instant updatedAt;

    // 业务方法
    public void start();
    public void complete(BatchStats stats, Instant committedAt);
    public void fail(String error, Instant committedAt);
    public boolean isCompleted();
    public boolean isSucceeded();
}

/**
 * 批次状态枚举（对应 ing_batch_status 字典）
 *
 * RUNNING: 执行中
 * SUCCEEDED: 成功完成
 * FAILED: 执行失败
 * SKIPPED: 已跳过（幂等检查）
 */
public enum BatchStatus {
    RUNNING("RUNNING", "运行中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    SKIPPED("SKIPPED", "已跳过");

    private final String code;
    private final String description;
}
```

#### CursorAggregate（游标聚合根）

```java
public class CursorAggregate {
    private Long id;

    // 游标标识（唯一键组合）
    private String provenanceCode;    // 来源代码：与 reg_provenance.provenance_code 一致
    private String operationCode;     // 操作类型：HARVEST/BACKFILL/UPDATE/METRICS
    private String cursorKey;         // 游标键：updated_at/published_at/seq_id/cursor_token 等
    private String namespaceScopeCode; // 命名空间范围：GLOBAL/EXPR/CUSTOM
    private String namespaceKey;      // 命名空间键：expr_hash 或自定义哈希；global=全0

    // 游标值（三种类型兼容）
    private String cursorTypeCode;    // 游标类型：TIME/ID/TOKEN
    private String cursorValue;       // 当前有效游标值（UTC ISO-8601/十进制字符串/不透明串）
    private String observedMaxValue;  // 观测到的最大边界

    // 归一化值（便于排序与范围查询）
    private Instant normalizedInstant;  // cursor_type=TIME 时填充（UTC）
    private BigDecimal normalizedNumeric; // cursor_type=ID 时填充（DECIMAL(38,0)）

    // Lineage（最近一次推进的追溯链路）
    private Long scheduleInstanceId;  // 最近一次推进的调度实例
    private Long planId;              // 最近一次推进关联 Plan
    private Long sliceId;             // 最近一次推进关联 Slice
    private Long taskId;              // 最近一次推进关联 Task
    private Long lastRunId;           // 最近一次推进的 Run
    private Long lastBatchId;         // 最近一次推进的 Batch
    private String exprHash;          // 最近推进使用的表达式哈希

    // 审计与版本控制
    private Long version;             // 乐观锁版本号（只允许向前推进）
    private Instant createdAt;
    private Instant updatedAt;

    // 业务方法
    /**
     * 推进游标（乐观锁保护）
     * @param newValue 新游标值
     * @param newInstant 新时间戳（TIME 类型）
     * @param newNumeric 新数值（ID 类型）
     * @param lineage 本次推进的追溯链路
     */
    public void advance(String newValue, Instant newInstant, BigDecimal newNumeric,
                       CursorLineage lineage);

    /**
     * 验证版本号（乐观锁检查）
     * @param expectedVersion 期望的版本号
     */
    public void validateVersion(Long expectedVersion);

    /**
     * 检查是否已被该 run 尝试推进（幂等检查）
     * @param runId 运行记录 ID
     * @return true 表示已推进，false 表示未推进
     */
    public boolean isAttemptedByRun(Long runId);
}

/**
 * 游标追溯链路（值对象）
 */
public record CursorLineage(
    Long scheduleInstanceId,
    Long planId,
    Long sliceId,
    Long taskId,
    Long runId,
    Long batchId,
    String exprHash
) {}

/**
 * 游标类型枚举（对应 ing_cursor_type 字典）
 */
public enum CursorType {
    TIME("TIME", "时间型"),
    ID("ID", "ID 型"),
    TOKEN("TOKEN", "令牌型");

    private final String code;
    private final String description;
}

/**
 * 命名空间范围枚举（对应 ing_namespace_scope 字典）
 */
public enum NamespaceScope {
    GLOBAL("GLOBAL", "全局"),
    EXPR("EXPR", "表达式级"),
    CUSTOM("CUSTOM", "自定义");

    private final String code;
    private final String description;
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

**说明**：以下表结构完全对应实际 SQL schema（`V0.1.0__init_ingest_schema.sql`），展示了调度层与执行层的完整数据模型。

#### ing_schedule_instance（调度实例表 - 调度层）

```sql
CREATE TABLE IF NOT EXISTS `ing_schedule_instance` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · 调度实例ID',
    `scheduler_code`    VARCHAR(32)     NOT NULL DEFAULT 'XXL' COMMENT 'DICT CODE(type=ing_scheduler)：调度器来源',
    `scheduler_job_id`  VARCHAR(64)     NULL COMMENT '外部 JobID（如 XXL 的 jobId）',
    `scheduler_log_id`  VARCHAR(64)     NULL COMMENT '外部运行/日志ID（如 XXL 的 logId）',
    `trigger_type_code` VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE' COMMENT 'DICT CODE(type=ing_trigger_type)：触发类型',
    `triggered_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '触发时间(UTC)',
    `trigger_params`    JSON            NULL COMMENT '调度入参(规范化)',
    `provenance_code`   VARCHAR(64)     NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致',
    -- 审计字段省略
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT ='调度实例：一次外部触发事件（作为本次编排的根）';
```

#### ing_plan（计划蓝图表 - 调度层）

```sql
CREATE TABLE IF NOT EXISTS `ing_plan` (
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · PlanID',
    `schedule_instance_id`       BIGINT UNSIGNED NOT NULL COMMENT '关联调度实例',
    `plan_key`                   VARCHAR(128)    NOT NULL COMMENT '人类可读/外部幂等键（唯一）',
    `provenance_code`            VARCHAR(64)     NULL COMMENT '冗余：来源代码',
    `endpoint_name`              VARCHAR(64)     NULL COMMENT '来源端点标识（search/detail/metrics 等）',
    `operation_code`             VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：采集类型 HARVEST/BACKFILL/UPDATE/METRICS',
    `expr_proto_hash`            CHAR(64)        NOT NULL COMMENT '表达式原型哈希',
    `expr_proto_snapshot`        JSON            NULL COMMENT '表达式原型快照（AST，JSON）',
    `provenance_config_snapshot` JSON            NULL COMMENT '来源配置快照（中立模型，JSON）',
    `provenance_config_hash`     CHAR(64)        NULL COMMENT '来源配置快照哈希',
    `slice_strategy_code`        VARCHAR(32)     NOT NULL COMMENT '切片策略：TIME/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/HYBRID 等',
    `slice_params`               JSON            NULL COMMENT '切片参数',
    `window_from`                TIMESTAMP(6)    NULL COMMENT '总窗起(含,UTC)',
    `window_to`                  TIMESTAMP(6)    NULL COMMENT '总窗止(不含,UTC)',
    `status_code`                VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DICT CODE(type=ing_plan_status)：DRAFT/SLICING/READY/PARTIAL/FAILED/COMPLETED',
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_key` (`plan_key`)
) ENGINE = InnoDB COMMENT ='计划蓝图：定义总窗口与切片策略（表达式原型）';
```

#### ing_plan_slice（计划切片表 - 调度层）

```sql
CREATE TABLE IF NOT EXISTS `ing_plan_slice` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · SliceID',
    `plan_id`              BIGINT UNSIGNED NOT NULL COMMENT '关联 Plan',
    `provenance_code`      VARCHAR(64)     NULL COMMENT '冗余：来源代码',
    `slice_no`             INT             NOT NULL COMMENT '切片序号(0..N)',
    `slice_signature_hash` CHAR(64)        NOT NULL COMMENT '切片签名哈希',
    `slice_spec`           JSON            NOT NULL COMMENT '切片边界说明（JSON）',
    `expr_hash`            CHAR(64)        NOT NULL COMMENT '局部化表达式哈希',
    `expr_snapshot`        JSON            NULL COMMENT '局部化表达式快照（AST，JSON）',
    `status_code`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_slice_status)：PENDING/DISPATCHED/EXECUTING/SUCCEEDED/FAILED/PARTIAL/CANCELLED',
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slice_unique` (`plan_id`, `slice_no`),
    UNIQUE KEY `uk_slice_sig` (`plan_id`, `slice_signature_hash`)
) ENGINE = InnoDB COMMENT ='计划切片：通用分片（时间/ID/token/预算），是并行与幂等的边界';
```

#### ing_task（任务表 - 执行层入口）

```sql
CREATE TABLE IF NOT EXISTS `ing_task` (
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'PK · TaskID',
    -- 上游关联（调度层）
    `schedule_instance_id` BIGINT UNSIGNED  NOT NULL COMMENT '冗余调度实例，便于聚合',
    `plan_id`              BIGINT UNSIGNED  NOT NULL,
    `slice_id`             BIGINT UNSIGNED  NOT NULL,
    -- 任务标识与配置
    `provenance_code`      VARCHAR(64)      NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致',
    `operation_code`       VARCHAR(32)      NOT NULL COMMENT 'DICT CODE(type=ing_operation)：操作类型 HARVEST/BACKFILL/UPDATE/METRICS',
    `params`               JSON             NULL COMMENT '任务参数(规范化)',
    `idempotent_key`       CHAR(64)         NOT NULL COMMENT 'SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))',
    `expr_hash`            CHAR(64)         NOT NULL COMMENT '冗余：执行表达式哈希',
    `priority`             TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '1高→9低',
    -- 任务租约（续租/回收）
    `lease_owner`          VARCHAR(128)     NULL COMMENT '执行期租约持有者（实例#线程）',
    `leased_until`         TIMESTAMP(6)     NULL COMMENT '租约到期时间(UTC)，过期视为可重新抢占',
    `lease_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '累计抢占/续租次数（监控/熔断用）',
    `last_heartbeat_at`    TIMESTAMP(6)     NULL COMMENT '执行期心跳时间',
    `retry_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '重试次数',
    `last_error_code`      VARCHAR(64)      NULL COMMENT '最近错误码',
    `last_error_msg`       VARCHAR(512)     NULL COMMENT '最近错误信息',
    -- 状态与时间线
    `status_code`          VARCHAR(32)      NOT NULL DEFAULT 'QUEUED' COMMENT 'DICT CODE(type=ing_task_status)：QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED',
    `scheduled_at`         TIMESTAMP(6)     NULL COMMENT '计划开始',
    `started_at`           TIMESTAMP(6)     NULL COMMENT '实际开始',
    `finished_at`          TIMESTAMP(6)     NULL COMMENT '结束',
    -- 追踪
    `scheduler_run_id`     VARCHAR(64)      NULL COMMENT '外部调度运行ID（若逐片触发才用）',
    `correlation_id`       VARCHAR(64)      NULL COMMENT 'Trace/CID',
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_idem` (`idempotent_key`),
    KEY `idx_task_queue` (`status_code`, `leased_until`, `priority`, `scheduled_at`, `id`)
) ENGINE = InnoDB COMMENT ='任务：每个切片生成一个任务；支持强幂等与调度/执行状态';
```

#### ing_task_run（执行记录表 - 执行层）

```sql
CREATE TABLE IF NOT EXISTS `ing_task_run` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · RunID',
    `task_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联任务',
    `attempt_no`       INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '尝试序号(1起)',
    -- 冗余字段（来源于 task 链路）
    `provenance_code`  VARCHAR(64)     NULL COMMENT '冗余：来源代码',
    `operation_code`   VARCHAR(32)     NULL COMMENT '冗余：操作类型',
    -- 状态与检查点
    `status_code`      VARCHAR(32)     NOT NULL DEFAULT 'PLANNED' COMMENT 'DICT CODE(type=ing_task_run_status)：PLANNED/RUNNING/SUCCEEDED/FAILED/CANCELLED',
    `checkpoint`       JSON            NULL COMMENT '运行级检查点（如 nextHint / resumeToken 等）',
    `stats`            JSON            NULL COMMENT '统计：fetched/upserted/failed/pages 等',
    `error`            TEXT            NULL COMMENT '失败原因',
    -- 时间型切片时冗余窗口
    `window_from`      TIMESTAMP(6)    NULL COMMENT '时间型切片时冗余窗口起(UTC)[含]',
    `window_to`        TIMESTAMP(6)    NULL COMMENT '时间型切片时冗余窗口止(UTC)[不含]',
    -- 时间线
    `started_at`       TIMESTAMP(6)    NULL,
    `finished_at`      TIMESTAMP(6)    NULL,
    `last_heartbeat`   TIMESTAMP(6)    NULL,
    -- 追踪
    `scheduler_run_id` VARCHAR(64)     NULL,
    `correlation_id`   VARCHAR(64)     NULL,
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_attempt` (`task_id`, `attempt_no`),
    KEY `idx_run_prov_op_status` (`provenance_code`, `operation_code`, `status_code`)
) ENGINE = InnoDB COMMENT ='任务运行（attempt）：一次具体尝试；失败重试/回放各自新增记录';
```

#### ing_task_run_batch（批次表 - 执行层）

```sql
CREATE TABLE IF NOT EXISTS `ing_task_run_batch` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · BatchID',
    `run_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联 Run',
    -- 冗余字段（便于回溯与聚合）
    `task_id`         BIGINT UNSIGNED NULL COMMENT '冗余 · Task',
    `slice_id`        BIGINT UNSIGNED NULL COMMENT '冗余 · Slice',
    `plan_id`         BIGINT UNSIGNED NULL COMMENT '冗余 · Plan',
    `expr_hash`       CHAR(64)        NULL COMMENT '冗余 · 执行表达式哈希',
    `provenance_code` VARCHAR(64)     NULL COMMENT '冗余：来源代码',
    `operation_code`  VARCHAR(32)     NULL COMMENT '冗余：操作类型',
    -- 批次标识与分页
    `batch_no`        INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '批次序号(1起,连续)',
    `page_no`         INT UNSIGNED    NULL COMMENT '页码（offset/limit；token 分页为空）',
    `page_size`       INT UNSIGNED    NULL COMMENT '页大小',
    -- 令牌分页（cursorMark/retstart 等）
    `before_token`    VARCHAR(512)    NULL COMMENT '该批开始令牌/位置（retstart/cursorMark等）',
    `after_token`     VARCHAR(512)    NULL COMMENT '该批结束令牌/下一位置',
    -- 幂等与统计
    `idempotent_key`  CHAR(64)        NOT NULL COMMENT 'SHA256(run_id + before_token | page_no)',
    `record_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
    `status_code`     VARCHAR(32)     NOT NULL DEFAULT 'RUNNING' COMMENT 'DICT CODE(type=ing_batch_status)：RUNNING/SUCCEEDED/FAILED/SKIPPED',
    `committed_at`    TIMESTAMP(6)    NULL,
    `error`           TEXT            NULL,
    `stats`           JSON            NULL,
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_batch_no` (`run_id`, `batch_no`),
    UNIQUE KEY `uk_run_before_tok` (`run_id`, `before_token`),
    UNIQUE KEY `uk_batch_idem` (`idempotent_key`),
    KEY `idx_batch_status_time` (`run_id`, `status_code`, `committed_at`)
) ENGINE = InnoDB COMMENT ='运行批次：页码/令牌步进的最小账目；承载断点续跑与去重';
```

#### ing_cursor（游标表 - 执行层）

```sql
CREATE TABLE IF NOT EXISTS `ing_cursor` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
    -- 游标标识（唯一键组合）
    `provenance_code`      VARCHAR(64)     NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致',
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL COMMENT '游标键：updated_at/published_at/seq_id/cursor_token 等',
    `namespace_scope_code` VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL' COMMENT 'DICT CODE(type=ing_namespace_scope)：GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT '命名空间键：expr_hash 或自定义哈希；global=全0',
    -- 游标值（三种类型兼容）
    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type)：TIME/ID/TOKEN',
    `cursor_value`         VARCHAR(1024)   NOT NULL COMMENT '当前有效游标值（UTC ISO-8601 / 十进制字符串 / 不透明串）',
    `observed_max_value`   VARCHAR(1024)   NULL COMMENT '观测到的最大边界',
    -- 归一化值（便于排序与范围查询）
    `normalized_instant`   TIMESTAMP(6)    NULL COMMENT 'cursor_type=time 时填充（UTC）',
    `normalized_numeric`   DECIMAL(38, 0)  NULL COMMENT 'cursor_type=id 时填充',
    -- Lineage（最近一次推进的追溯链路）
    `schedule_instance_id` BIGINT UNSIGNED NULL COMMENT '最近一次推进的调度实例',
    `plan_id`              BIGINT UNSIGNED NULL COMMENT '最近一次推进关联 Plan',
    `slice_id`             BIGINT UNSIGNED NULL COMMENT '最近一次推进关联 Slice',
    `task_id`              BIGINT UNSIGNED NULL COMMENT '最近一次推进关联 Task',
    `last_run_id`          BIGINT UNSIGNED NULL COMMENT '最近一次推进的 Run',
    `last_batch_id`        BIGINT UNSIGNED NULL COMMENT '最近一次推进的 Batch',
    `expr_hash`            CHAR(64)        NULL COMMENT '最近推进使用的表达式哈希',
    -- 审计与版本控制
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（只允许向前推进）',
    -- 审计字段省略（created_at/updated_at/created_by 等）
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cursor_ns` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`, `namespace_key`),
    KEY `idx_cursor_sort_time` (`cursor_type_code`, `normalized_instant`),
    KEY `idx_cursor_sort_id` (`cursor_type_code`, `normalized_numeric`)
) ENGINE = InnoDB COMMENT ='通用水位·当前值：(source_code + operation + cursor_key + namespace) 唯一；兼容 time/id/token';
```

#### ing_cursor_event（游标事件表 - 执行层）

```sql
CREATE TABLE IF NOT EXISTS `ing_cursor_event` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
    -- 游标标识（与 ing_cursor 对应）
    `provenance_code`      VARCHAR(64)     NOT NULL,
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL,
    `namespace_scope_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_namespace_scope)：GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL,
    -- 游标推进信息
    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type)：TIME/ID/TOKEN',
    `prev_value`           VARCHAR(1024)   NULL,
    `new_value`            VARCHAR(1024)   NOT NULL,
    `observed_max_value`   VARCHAR(1024)   NULL,
    -- 归一化值（便于查询）
    `prev_instant`         TIMESTAMP(6)    NULL,
    `new_instant`          TIMESTAMP(6)    NULL,
    `prev_numeric`         DECIMAL(38, 0)  NULL,
    `new_numeric`          DECIMAL(38, 0)  NULL,
    -- 覆盖窗口
    `window_from`          TIMESTAMP(6)    NULL COMMENT '覆盖窗口起(UTC)[含]',
    `window_to`            TIMESTAMP(6)    NULL COMMENT '覆盖窗口止(UTC)[不含]',
    `direction_code`       VARCHAR(16)     NULL COMMENT 'DICT CODE(type=ing_cursor_direction)：FORWARD/BACKFILL',
    -- 幂等与追溯
    `idempotent_key`       CHAR(64)        NOT NULL COMMENT '事件幂等键：SHA256(source,op,key,ns_scope,ns_key,prev->new,ingestWindow,run_id,...)',
    `schedule_instance_id` BIGINT UNSIGNED NULL,
    `plan_id`              BIGINT UNSIGNED NULL,
    `slice_id`             BIGINT UNSIGNED NULL,
    `task_id`              BIGINT UNSIGNED NULL,
    `run_id`               BIGINT UNSIGNED NULL,
    `batch_id`             BIGINT UNSIGNED NULL,
    `expr_hash`            CHAR(64)        NULL,
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cur_evt_idem` (`idempotent_key`),
    KEY `idx_cur_evt_timeline` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`, `namespace_key`),
    KEY `idx_cur_evt_window` (`window_from`, `window_to`)
) ENGINE = InnoDB COMMENT ='水位推进事件（不可变）：每次成功推进一条事件；支持回放与全链路回溯';
```

#### ing_outbox_message（消息发件箱表 - 执行层）

```sql
CREATE TABLE IF NOT EXISTS `ing_outbox_message` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · OutboxID',
    `aggregate_type`   VARCHAR(32)     NOT NULL COMMENT '聚合类型：如 TASK/PLAN/...；用于审计与回放定位',
    `aggregate_id`     BIGINT UNSIGNED NOT NULL COMMENT '聚合根ID；任务场景=ing_task.id',
    `channel`          VARCHAR(64)     NOT NULL COMMENT '逻辑通道=目标Topic，如 ingest.task',
    `op_type`          VARCHAR(32)     NOT NULL COMMENT '业务语义标签：如 TASK_READY / EVENT_PUBLISHED',
    `partition_key`    VARCHAR(128)    NOT NULL COMMENT '分片/顺序路由键；建议 "provenance:operation"；用于顺序投递或分区限流',
    `dedup_key`        VARCHAR(128)    NOT NULL COMMENT '幂等键；任务=ing_task.idempotent_key；(channel, dedup_key) 唯一',
    `payload_json`     JSON            NOT NULL COMMENT '最小必要载荷(JSON)：taskId/sliceKey/planKey/provenance/operation/endpoint/priority/notBefore 等',
    `headers_json`     JSON            NULL COMMENT '扩展头(JSON)：correlationId 等',
    `not_before`       TIMESTAMP(6)    NULL COMMENT '最早可发布时间(UTC)：NULL=随时可发；用于定时/延时发布',
    -- 发布状态
    `status_code`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '发布状态：PENDING/PUBLISHING/PUBLISHED/FAILED/DEAD',
    `retry_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '发布重试次数（失败则+1）',
    `next_retry_at`    TIMESTAMP(6)    NULL COMMENT '下次尝试发布时间(UTC)，配合退避曲线使用',
    `error_code`       VARCHAR(64)     NULL COMMENT '最近一次发布错误码',
    `error_msg`        VARCHAR(512)    NULL COMMENT '最近一次发布错误详情',
    -- 发布器租约
    `pub_lease_owner`  VARCHAR(128)    NULL COMMENT '发布器租约持有者（实例ID或workerId）',
    `pub_leased_until` TIMESTAMP(6)    NULL COMMENT '发布器租约到期(UTC)，过期可被其他发布器接管',
    `msg_id`           VARCHAR(128)    NULL COMMENT 'Broker 返回的消息ID（对账/回放标识）',
    -- 审计字段省略
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_channel_dedup` (`channel`, `dedup_key`),
    KEY `idx_outbox_status_time` (`status_code`, `not_before`, `id`),
    KEY `idx_outbox_partition` (`channel`, `partition_key`, `status_code`)
) ENGINE = InnoDB COMMENT ='Outbox：通用出站消息表（任务推送/集成事件统一托管；与业务写入同事务；Relay扫描并投递到MQ）';
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
| LeaseRevokedException（Critical-3） | 快速失败，立即终止任务执行，记录 WARN 日志 | 不变 | 否 |
| ConfigurationTamperedException | 标记任务 FAILED，记录 ERROR 日志 | FAILED | 否 |
| ExpressionCompilationException | 标记任务 FAILED，记录 ERROR 日志 | FAILED | 否 |
| BatchPlanningException | 标记任务 FAILED，记录 ERROR 日志，回滚已插入批次 | FAILED | 是（MQ 重试） |
| BatchExecutionException | 标记批次 FAILED，继续执行其他批次 | 部分失败 | 是（批次级重试） |
| CursorAdvancementException（Critical-2） | 标记任务 CURSOR_PENDING，触发异步重试 | CURSOR_PENDING | 是（游标重试任务） |
| DatabaseException | 抛出异常，触发 MQ 重试 | 不变 | 是（MQ 重试） |
| MinIOException | 重试 3 次，仍失败则标记批次 FAILED | 部分失败 | 是（批次级重试） |

**关键异常说明**：

1. **LeaseRevokedException**（心跳续租失败导致租约被接管）
   - **触发条件**：心跳续租连续失败 3 次后，验证发现租约已被其他节点接管
   - **处理策略**：立即终止当前任务执行，清理资源，不修改任务状态
   - **日志级别**：WARN（非错误，只是节点间正常的任务迁移）
   - **告警**：否（正常行为）

2. **CursorAdvancementException**（游标推进失败）
   - **触发条件**：乐观锁冲突、数据库异常导致游标推进失败
   - **处理策略**：
     - 标记任务状态为 `CURSOR_PENDING`
     - 触发 `CursorAdvancementRetryService` 异步重试
     - 最多重试 5 次
     - 超过 5 次后发送 Critical 告警，需人工介入
   - **日志级别**：ERROR（首次失败）、CRITICAL（超过阈值）
   - **告警**：是（Critical 级别，影响数据一致性）

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
        max-batches-per-execution: 1000  # 单任务最大批次数（Critical-5 优化）
        default-page-size: 500
        execution-mode: SEQUENTIAL  # SEQUENTIAL | PARALLEL
        parallel-threads: 5
        insert-chunk-size: 100      # 批次记录分批插入大小（Critical-5 优化）
      
      # 重试配置
      retry:
        max-attempts: 3
        backoff-multiplier: 2
        initial-interval-ms: 1000
      
      # 心跳配置
      heartbeat:
        thread-pool-size: 10
        batch-renewal-enabled: true  # 批量心跳续租（性能优化）
        batch-renewal-size: 50       # 单次批量续租的最大任务数
        consecutive-failure-threshold: 3  # 连续失败阈值
      
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

**重要**: 指标收集注解应添加在 Application 层的实现类上，而非 Domain 层，以保持 Domain 层的纯粹性。

```java
// Application 层 - TaskExecutionOrchestrator 实现类
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionOrchestrator implements TaskExecutionUseCase {
    
    private final MeterRegistry meterRegistry;
    
    @Timed(value = "ingest.task.execution.duration", description = "任务执行耗时")
    @Counted(value = "ingest.task.execution.total", description = "任务执行总数")
    @Override
    public TaskExecutionResult execute(TaskExecutionCommand command) {
        // 编排逻辑
        try {
            TaskExecutionResult result = doExecute(command);
            
            // 记录成功指标
            meterRegistry.counter("ingest.task.execution.result",
                Tags.of("status", result.getStatus().name()))
                .increment();
            
            return result;
        } catch (Exception e) {
            // 记录失败指标
            meterRegistry.counter("ingest.task.execution.result",
                Tags.of("status", "ERROR"))
                .increment();
            throw e;
        }
    }
}

// Infrastructure 层 - 批次执行指标
@Component
public class PubMedBatchExecutor implements BatchExecutor {
    
    private final MeterRegistry meterRegistry;
    
    @Override
    public BatchExecutionResult execute(BatchExecutionContext context) {
        Instant start = Instant.now();
        try {
            BatchExecutionResult result = doExecute(context);
            
            // 记录批次执行指标
            Duration duration = Duration.between(start, Instant.now());
            meterRegistry.timer("ingest.batch.execution.duration",
                Tags.of("provenance", context.getProvenanceCode(), "status", "SUCCESS"))
                .record(duration);
            
            meterRegistry.counter("ingest.batch.execution.records",
                Tags.of("provenance", context.getProvenanceCode()))
                .increment(result.getRecordCount());
            
            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            meterRegistry.timer("ingest.batch.execution.duration",
                Tags.of("provenance", context.getProvenanceCode(), "status", "FAILED"))
                .record(duration);
            throw e;
        }
    }
}

// Infrastructure 层 - 租约抢占指标
@Component
public class LeaseAcquisitionServiceImpl implements LeaseAcquisitionService {
    
    private final MeterRegistry meterRegistry;
    
    @Override
    public LeaseAcquisitionResult tryAcquire(Long taskId, String leaseOwner, int leaseDuration) {
        LeaseAcquisitionResult result = doTryAcquire(taskId, leaseOwner, leaseDuration);
        
        meterRegistry.counter("ingest.lease.acquisition",
            Tags.of("result", result.isSuccess() ? "success" : "failed"))
            .increment();
        
        return result;
    }
}

// Infrastructure 层 - 游标推进指标
@Component
public class TimestampCursorAdvancer implements CursorAdvancer {
    
    private final MeterRegistry meterRegistry;
    
    @Override
    public CursorAdvancementResult advance(CursorAdvancementContext context) {
        CursorAdvancementResult result = doAdvance(context);
        
        meterRegistry.counter("ingest.cursor.advancement",
            Tags.of("provenance", context.getProvenanceCode(), 
                    "operation", context.getOperationType().name()))
            .increment();
        
        return result;
    }
}
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
- **孤儿批次清理任务**（Critical-5 修正）：定时清理无效批次记录
- **监控对象存储空间**: 设置存储配额和清理策略
- **数据库索引优化**: 定期分析慢查询并优化索引
- **租约超时调优**: 根据任务平均执行时间调整租约时长
- **心跳续租性能监控**（Critical-4 修正）：监控 P99 延迟，必要时启用批量续租

#### 孤儿批次清理任务设计

**背景**：批次规划失败可能留下孤儿批次（已创建但未关联活跃 run 的批次记录）

**清理策略**：

```java
@Component
public class OrphanBatchCleanupTask {
    private final BatchRepository batchRepository;
    private final TaskRunRepository taskRunRepository;

    /**
     * 定时清理孤儿批次
     * 执行频率：每 6 小时
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void cleanupOrphanBatches() {
        // 1. 查找 24 小时前创建的批次记录
        Instant cutoffTime = Instant.now().minus(Duration.ofHours(24));

        // 2. 找出这些批次中，run_id 不在活跃 run 列表中的（孤儿批次）
        List<Long> orphanBatchIds = batchRepository.findOrphanBatchIds(cutoffTime);

        if (orphanBatchIds.isEmpty()) {
            log.info("[INGEST][INFRA] No orphan batches found");
            return;
        }

        // 3. 批量删除孤儿批次（每次最多 1000 条）
        List<List<Long>> chunks = Lists.partition(orphanBatchIds, 1000);
        int totalDeleted = 0;

        for (List<Long> chunk : chunks) {
            int deleted = batchRepository.batchDelete(chunk);
            totalDeleted += deleted;
        }

        log.info("[INGEST][INFRA] Orphan batches cleaned: count={}", totalDeleted);

        // 4. 记录指标
        meterRegistry.counter("ingest.cleanup.orphan_batches")
                .increment(totalDeleted);
    }
}

/**
 * 查找孤儿批次的 SQL
 *
 * SELECT b.id
 * FROM ing_task_run_batch b
 * WHERE b.created_at < ?
 *   AND b.run_id NOT IN (
 *     SELECT id FROM ing_task_run
 *     WHERE status IN ('RUNNING', 'PENDING')
 *   )
 * LIMIT 10000;
 */
```

**清理触发条件**：
- 批次创建时间 > 24 小时
- 关联的 run_id 不在活跃状态（RUNNING、PENDING）

**安全措施**：
- 单次最多清理 10000 条
- 分批删除（每批 1000 条）
- 记录清理日志与指标
- 仅删除确认的孤儿批次（二次确认 run 状态）

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

**性能优化演进路径**（Critical-4 修正）:

1. **短期优化（当前阶段，节点数 < 20）**
   - 实施批量心跳续租（单节点的所有任务合并为一条 SQL）
   - 配置项：`patra.ingest.task-execution.heartbeat.batch-renewal-enabled=true`
   - 效果：将 N 个任务的 N 次数据库更新合并为 1 次，性能提升 N 倍

2. **中期优化（节点数 20-50）**
   - 引入 Redis 混合模式：
     - 心跳写入 Redis（高频、轻量）
     - 每 60 秒同步到 MySQL（持久化、恢复）
     - 租约抢占仍走 MySQL CAS（保证一致性）
   - 优点：大幅降低 MySQL 写压力
   - 缺点：增加 Redis 依赖，节点故障恢复延迟增加

3. **长期优化（节点数 > 50）**
   - 升级到 etcd 或 Consul：
     - 专业的分布式协调服务
     - 支持更高的并发与更低的延迟
     - 原生支持租约（Lease）、心跳（KeepAlive）
   - 权衡：增加运维复杂度，但性能与可靠性显著提升

**性能监控指标**：
- `ingest.heartbeat.renewal.duration.p99`：心跳续租 P99 延迟
- 阈值：< 200ms（否则告警并考虑启用优化）
- `ingest.heartbeat.batch.renewal.count`：批量续租次数与合并效果

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

**优化措施**（Critical-5 修正）:

1. **批次数限制**
   - 配置项：`patra.ingest.task-execution.batch.max-batches-per-execution=1000`
   - 单任务最多生成 1000 个批次
   - 超出时可选：拒绝任务 / 拆分为多个子任务 / 只执行前 N 批

2. **分批插入**
   - 配置项：`patra.ingest.task-execution.batch.insert-chunk-size=100`
   - 批次记录每 100 条提交一次事务
   - 避免单次插入过多导致的锁竞争与内存压力

3. **孤儿批次清理**
   - 定时任务：每 6 小时扫描一次
   - 清理条件：创建时间 > 24 小时 且 run_id 不在活跃状态
   - 安全措施：单次最多清理 10000 条，分批删除（每批 1000 条）
   - 详见 Deployment Considerations > 运维建议 > 孤儿批次清理任务设计

4. **批次规划失败回滚**
   - 批次规划失败时，标记任务为 FAILED
   - 已插入的批次保留（便于排查问题）
   - 由孤儿清理任务自动清理

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

