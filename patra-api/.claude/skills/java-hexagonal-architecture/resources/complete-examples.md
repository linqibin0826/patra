# 完整示例 - 可运行代码

> **10 分钟学习** → 通过真实的 Plan 采集功能理解六边形架构 + DDD 实践

---

## 🚀 快速导航

**学习路径**:
1. [快速启动](#快速启动) - 3 分钟理解核心流程
2. [分层详解](#架构分层详解) - 深入每一层的实现
3. [重构示例](#重构示例-从错误到正确) - 从错误学习正确做法
4. [端到端流程](#端到端请求流程) - 完整的请求追踪
5. [测试策略](#测试策略) - 如何测试每一层

---

## 🚀 快速启动

### Plan 采集功能概览

**业务场景**: 调度器触发数据采集任务,系统基于时间窗口和配置快照创建包含多个 Slice 和 Task 的 Plan。

**核心流程** (3 步):

```java
步骤 1 (Domain): 建模 Plan 聚合 → 封装业务规则
步骤 2 (Application): 编排流程 → 协调 6 个阶段
步骤 3 (Adapter + Infra): 触发 + 持久化 → XXL-Job + MyBatis-Plus
```

<details>
<summary><b>查看 3 步核心代码</b></summary>

```java
// 步骤 1: Domain 层 - Plan 聚合根
@Getter
public class PlanAggregate extends AggregateRoot<Long> {
    private final String planKey;  // 业务幂等键
    private PlanStatus status;

    public void markAsCompleted() {
        if (this.status == PlanStatus.CANCELLED) {
            throw new IllegalStateException("不能完成已取消的 plan");
        }
        this.status = PlanStatus.COMPLETED;
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}

// 步骤 2: Application 层 - 编排者
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
    @Override
    @Transactional
    public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
        // 阶段 1: 准备 → 阶段 2: 组装 → 阶段 3: 幂等检查
        // → 阶段 4: 持久化 → 阶段 5: 发布事件
    }
}

// 步骤 3a: Adapter 层 - XXL-Job 触发
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {
    @XxlJob("pubmedHarvest")
    public void run() {
        executeScheduleJob(XxlJobHelper.getJobParam());
    }
}

// 步骤 3b: Infrastructure 层 - 仓储实现
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {
    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanDO entity = planConverter.toEntity(plan);
        planMapper.insert(entity);
        return planConverter.toAggregate(entity);
    }
}
```
</details>

**关键设计决策**:
- ✅ **幂等性**: planKey (数据源 + 操作 + 窗口 + 策略哈希) 防止重复
- ✅ **事务边界**: 单个 @Transactional 在编排器层级
- ✅ **Outbox 模式**: 持久化和事件发布的原子性
- ✅ **协调者模式**: 按关注点分离 (持久化、幂等性、发布)

---

## 📊 架构分层概览

| 层 | 职责 | 关键类 | 依赖方向 |
|-----|------|--------|----------|
| **Adapter** | 接收触发 | `PubmedHarvestJob` | → App |
| **Application** | 编排流程 | `PlanIngestionOrchestrator` + Coordinators | → Domain |
| **Domain** | 业务规则 | `PlanAggregate` + `PlanStatus` + `PlanRepository` (接口) | ← Infra |
| **Infrastructure** | 数据访问 | `PlanRepositoryMpImpl` + `PlanDO` + `PlanConverter` | → Domain |

---

## 🏗️ 架构分层详解

Plan 采集功能演示了一个完整的六边形架构 + DDD 实现,跨越所有四层。

### 1. Domain 层 (纯 Java - 业务核心)

**目的**: 业务逻辑和规则。禁止框架依赖。

#### Aggregate Root: PlanAggregate

<details>
<summary><b>查看完整实现</b> (PlanAggregate.java)</summary>

```java
/// 表示采集计划蓝图的聚合根,包含状态转换。
/// 
/// 幂等性: planKey (数据源 + 操作 + 窗口 + 策略哈希) 防止重复
/// 状态机: DRAFT → SLICING → READY/PARTIAL → COMPLETED/FAILED
/// 
/// 线程安全: 仅单线程使用,不跨线程共享
@Getter
public class PlanAggregate extends AggregateRoot<Long> {

  /// 调度实例标识符
  private final Long scheduleInstanceId;

  /// 业务幂等键,用于去重
  private final String planKey;

  /// Provenance/数据源代码 (例如: PUBMED)
  private final String provenanceCode;

  /// 操作类型 (全量、增量、补偿)
  private final OperationCode operationCode;

  /// Plan 表达式原型的哈希值
  private final String exprProtoHash;

  /// 表达式原型的快照 (JSON)
  private final String exprProtoSnapshotJson;

  /// Provenance 配置的快照
  private final String provenanceConfigSnapshotJson;

  /// 窗口边界规范
  private final WindowSpec windowSpec;

  /// 切片策略代码 (TIME, DATE, SINGLE)
  private final String sliceStrategyCode;

  /// Plan 的当前状态
  private PlanStatus status;

  // 私有构造函数确保使用工厂方法
  private PlanAggregate(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      OperationCode operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status) {
    super(id);
    this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId);
    this.planKey = Objects.requireNonNull(planKey);
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.exprProtoHash = exprProtoHash;
    this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
    this.windowSpec = Objects.requireNonNull(windowSpec);
    this.sliceStrategyCode = sliceStrategyCode;
    this.status = status == null ? PlanStatus.DRAFT : status;
  }

  // 用于创建新 plan 的工厂方法
  public static PlanAggregate create(/* parameters */) {
    // 业务规则验证在此处进行
    return new PlanAggregate(/* ... */);
  }

  // 业务逻辑方法
  public void markAsCompleted() {
    // 业务规则: 验证状态转换
    if (this.status == PlanStatus.CANCELLED) {
      throw new IllegalStateException("不能完成已取消的 plan");
    }
    this.status = PlanStatus.COMPLETED;
    // 发出领域事件用于跨聚合反应
  }
}
```

**核心要点**:
- ✅ 纯 Java (继承自 patra-common 的 AggregateRoot)
- ✅ 具有业务意义的不可变字段
- ✅ 使用工厂方法创建
- ✅ 业务规则在领域方法中
- ❌ 禁止 Spring 注解 (@Service, @Autowired)
- ❌ 禁止框架依赖

---

#### Value Object: BatchSchedule

**文件**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/batch/BatchSchedule.java`

```java
/// 表示批次调度结果的值对象。
/// 
/// 不变量:
/// - batches 不能为 null (但可以为空)
/// - totalBatches 必须 >= 0
public record BatchSchedule(
    List<Batch> batches,
    int totalBatches,
    boolean exceedsLimit) {

  // 紧凑构造函数用于验证
  public BatchSchedule {
    if (batches == null) {
      throw new IllegalArgumentException("batches 不能为 null");
    }
    if (totalBatches < 0) {
      throw new IllegalArgumentException("totalBatches 不能为负数");
    }
  }

  /// 创建一个空的批处理计划
  public static BatchPlan empty() {
    return new BatchPlan(List.of(), 0, false);
  }

  /// 创建包含单个批次的计划
  public static BatchPlan single(Batch batch) {
    return new BatchPlan(List.of(batch), 1, false);
  }

  /// 当计划至少包含一个批次时返回 true
  public boolean hasBatches() {
    return !batches.isEmpty();
  }
}
```

**核心要点**:
- ✅ 使用 `record` 创建不可变值对象
- ✅ 紧凑构造函数验证不变量
- ✅ 常见场景使用工厂方法
- ✅ 按值比较 (record 自动实现)
- ✅ 自我验证

---

#### Port Interface: PlanRepository

**文件**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PlanRepository.java`

```java
/// Plan 持久化的端口接口 (定义在 domain 层)。
/// 
/// Infrastructure 层实现此接口。
public interface PlanRepository {

  /// 保存 Plan: 基于 ID 是否存在来决定插入或更新
/// @param plan 聚合
/// @return 已持久化的聚合,ID 已填充
  PlanAggregate save(PlanAggregate plan);

  /// 通过业务键查找 plan (幂等查询)
/// @param planKey 业务幂等键
/// @return 如果找到则返回 plan
  Optional<PlanAggregate> findByPlanKey(String planKey);

  /// 检查 planKey 是否存在
/// @param planKey 业务键
/// @return 如果存在则返回 true
  boolean existsByPlanKey(String planKey);
}
```

**核心要点**:
- ✅ 端口定义在 domain 层
- ✅ 签名中使用领域类型 (PlanAggregate)
- ✅ 面向业务的方法名
- ❌ 禁止基础设施类型 (DOs, DTOs)

---

### 2. Application 层 (编排)

**目的**: 协调用例,管理事务,委托给 domain 和 infrastructure。

#### Orchestrator: PlanIngestionOrchestrator

**文件**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java`

```java
/// Plan 采集流程的主编排器。
/// 
/// 协调六个阶段:
/// 1. 持久化调度实例并加载 provenance 配置快照
/// 2. 查询游标水位并解析执行窗口
/// 3. 构建 plan 表达式并运行预验证
/// 4. 组装 plan/slices/tasks (带幂等性)
/// 5. 检查现有 plan (幂等重用)
/// 6. 持久化并发布任务入队事件 (Outbox 模式)
/// 
/// 此编排器维护 @Transactional 边界以确保持久化和事件发布的原子性 (Outbox 模式)。
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  // Domain 端口
  private final PatraRegistryPort patraRegistryPort;
  private final CursorRepository cursorRepository;
  private final TaskRepository taskRepository;
  private final PlanRepository planRepository;

  // Application 服务
  private final PlanningWindowResolver planningWindowResolver;
  private final PlannerValidator plannerValidator;
  private final PlanAssembler planAssembler;
  private final PlanExpressionBuilder planExpressionBuilder;

  // Coordinators 用于委托特定职责
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  /// 主 plan 编排流程 (入口方法)
  @Override
  @Transactional  // 编排器级别的事务边界
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    logPlanIngestionStart(request);

    // 阶段 1: 准备规划上下文
    PlanningContext context = preparePlanningContext(request);

    // 阶段 2: 构建 plan 表达式
    PlanExpressionDescriptor expressionDescriptor = buildPlanExpression(context);

    // 阶段 3: 预验证
    performPreValidation(context);

    // 阶段 4: 组装 plan
    PlanAssemblyResult assembly = assembleAndValidatePlan(context, expressionDescriptor);

    // 阶段 5: 检查现有 plan (幂等性)
    PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());
    if (existingPlan != null) {
      return idempotencyCoordinator.handleIdempotentPlanReuse(
          existingPlan, context.schedule(), assembly.plan().getPlanKey());
    }

    // 阶段 6: 持久化并发布
    return persistAndPublishNewPlan(
        assembly.plan(), assembly, context.schedule(), context.window());
  }

  /// 通过加载配置并解析窗口来准备规划上下文
  private PlanningContext preparePlanningContext(PlanIngestionCommand request) {
    log.debug("为 provenance [{}] operation [{}] 准备规划上下文",
        request.provenanceCode(), request.operationCode());

    // 委托给持久化 coordinator
    ScheduleInstanceAggregate schedule =
        persistenceCoordinator.persistScheduleInstance(request);

    // 从 registry 获取配置快照
    ProvenanceConfigSnapshot configSnapshot =
        patraRegistryPort.fetchConfig(request.provenanceCode(), request.operationCode());

    PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

    // 查询游标水位
    Instant cursorWatermark =
        lookupCursorWatermark(request.provenanceCode(), request.operationCode());

    // 解析规划窗口
    PlannerWindow window =
        resolvePlannerWindow(norm, configSnapshot, cursorWatermark, request.triggeredAt());

    return new PlanningContext(
        schedule, configSnapshot, norm, window,
        request.provenanceCode(), request.operationCode());
  }

  /// 持久化新 plan 及其 slices 和 tasks,然后发布排队的事件
  private PlanIngestionResult persistAndPublishNewPlan(
      PlanAggregate draftPlan,
      PlanAssemblyResult assembly,
      ScheduleInstanceAggregate schedule,
      PlannerWindow window) {

    log.debug("为 provenance [{}] operation [{}] 持久化 plan: planKey={}",
        draftPlan.getProvenanceCode(), draftPlan.getOperationCode(), draftPlan.getPlanKey());

    // 委托给持久化 coordinator
    PlanAggregate persistedPlan = persistenceCoordinator.savePlan(draftPlan);
    List<PlanSliceAggregate> persistedSlices =
        persistenceCoordinator.persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistenceCoordinator.persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    log.debug("已持久化 plan [{}],包含 {} 个 slices 和 {} 个 tasks",
        persistedPlan.getId(), persistedSlices.size(), persistedTasks.size());

    // 委托给发布 coordinator
    List<TaskQueuedEvent> queuedEvents =
        publishingCoordinator.collectQueuedEvents(persistedTasks);
    publishingCoordinator.publishNewPlanEvents(queuedEvents, persistedPlan, schedule);

    log.info("成功为 provenance [{}] operation [{}] 创建 plan [{}]: "
        + "为窗口 [{}, {}] 生成了 {} 个 slices 和 {} 个 tasks",
        persistedPlan.getId(), persistedPlan.getProvenanceCode(),
        persistedPlan.getOperationCode(), persistedSlices.size(), persistedTasks.size(),
        window == null ? null : window.from(), window == null ? null : window.to());

    return publishingCoordinator.buildIngestionResult(
        schedule, persistedPlan, persistedSlices, persistedTasks.size(),
        assembly.status().name());
  }

  // 保存规划上下文数据的内部记录
  private record PlanningContext(
      ScheduleInstanceAggregate schedule,
      ProvenanceConfigSnapshot configSnapshot,
      PlanTriggerNorm norm,
      PlannerWindow window,
      ProvenanceCode provenanceCode,
      OperationCode operationCode) {}
}
```

**核心要点**:
- ✅ 仅负责编排,业务逻辑委托给 Domain
- ✅ 在编排器级别使用 @Transactional (事务边界)
- ✅ 使用 Coordinator 模式分离关注点
- ✅ 清晰的阶段划分并记录日志
- ❌ 禁止业务规则 (应在 Domain 中)
- ❌ 禁止直接数据库访问 (使用 ports)

---

#### Coordinator: PlanPersistenceCoordinator

**文件**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanPersistenceCoordinator.java`

```java
/// Plan 持久化操作的 Coordinator。
/// 
/// 负责安全地持久化 plan 聚合、slices、tasks 和 schedule 实例,
/// 并进行适当的异常处理和日志记录。
/// 
/// 注意: 此 coordinator 不使用 @Transactional。它依赖于主编排器的外部事务边界。
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {

  private final PlanRepository planRepository;
  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final ScheduleInstanceRepository scheduleInstanceRepository;

  /// 保存或更新 schedule 实例 (幂等)
  public ScheduleInstanceAggregate persistScheduleInstance(PlanIngestionCommand request) {
    ScheduleInstanceAggregate schedule = ScheduleInstanceAggregate.start(
        request.scheduler(),
        request.schedulerJobId(),
        request.schedulerLogId(),
        request.triggerType(),
        request.triggeredAt(),
        request.triggerParams(),
        request.provenanceCode().getCode());

    try {
      return scheduleInstanceRepository.saveOrUpdateInstance(schedule);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.SCHEDULE_INSTANCE,
          "持久化 schedule 实例失败",
          ex);
    }
  }

  /// 持久化 plan 聚合并包装底层异常
  public PlanAggregate savePlan(PlanAggregate draftPlan) {
    try {
      return planRepository.save(draftPlan);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN,
          "持久化 plan 聚合失败",
          ex);
    }
  }

  /// 批量持久化 plan slice 聚合
  public List<PlanSliceAggregate> persistSlices(
      PlanAggregate plan,
      List<PlanSliceAggregate> slices) {
    if (CollUtil.isEmpty(slices)) {
      return List.of();
    }

    // 将 plan ID 绑定到每个 slice
    slices.forEach(slice -> slice.bindPlan(plan.getId()));

    try {
      return planSliceRepository.saveAll(slices);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN_SLICE,
          "持久化 plan slices 失败",
          ex);
    }
  }

  /// 批量持久化 task 聚合并绑定 plan 和 slice ID
  public List<TaskAggregate> persistTasks(
      PlanAggregate plan,
      List<PlanSliceAggregate> persistedSlices,
      List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }

    // 创建 slice 查找映射
    Map<Integer, PlanSliceAggregate> sliceBySeq =
        MapUtil.newHashMap(persistedSlices.size());
    for (PlanSliceAggregate slice : persistedSlices) {
      sliceBySeq.putIfAbsent(slice.getSliceNo(), slice);
    }

    // 将 plan 和 slice ID 绑定到每个 task
    for (TaskAggregate task : tasks) {
      Long placeholderSequence = task.getSliceId();
      PlanSliceAggregate slice = ObjectUtil.isNull(placeholderSequence)
          ? null
          : sliceBySeq.get(placeholderSequence.intValue());
      task.bindPlanAndSlice(plan.getId(), slice == null ? null : slice.getId());
    }

    try {
      return taskRepository.saveAll(tasks);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.TASK,
          "持久化 tasks 失败",
          ex);
    }
  }
}
```

**核心要点**:
- ✅ Coordinator 分离持久化关注点
- ✅ 使用领域特定类型包装异常
- ✅ 批量操作以提高性能
- ✅ 禁止 @Transactional (依赖外部边界)
- ✅ 清晰的日志用于调试

---

### 3. Infrastructure 层 (被驱动)

**目的**: 实现 domain 端口,提供数据访问。

#### Repository 实现: PlanRepositoryMpImpl

**文件**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/PlanRepositoryMpImpl.java`

```java
/// PlanRepository 的 MyBatis-Plus 实现 (Infrastructure 层)。
/// 
/// 职责:
/// - 在 PlanAggregate 和 PlanDO 之间进行映射 (使用 MapStruct)
/// - 通过 planKey 进行幂等查询 / 存在性检查
/// - 插入 / 更新 (通过 @Version 实现乐观锁)
/// 
/// 日志策略:
/// - DEBUG: 在插入/更新时记录关键字段 (id, planKey)
/// - INFO: 避免嘈杂的高频 CRUD 日志
/// 
/// 线程安全: 通过依赖注入实现的无状态单例
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

  /// Plan mapper (MyBatis-Plus)
  private final PlanMapper planMapper;

  /// 聚合到 DO 的转换器 (MapStruct)
  private final PlanConverter planConverter;

  /// 保存 Plan: 基于 ID 是否存在来决定插入或更新。
/// 
/// 将聚合转换为 DO 并转换回来,以确保反映 version/自增字段。
  @Override
  public PlanAggregate save(PlanAggregate plan) {
    PlanDO entity = planConverter.toEntity(plan);

    if (entity.getId() == null) {
      // 插入新 plan
      if (log.isDebugEnabled()) {
        log.debug("插入 plan planKey={}", entity.getPlanKey());
      }
      planMapper.insert(entity);
    } else {
      // 更新现有 plan
      if (log.isDebugEnabled()) {
        log.debug("更新 plan id={} planKey={}", entity.getId(), entity.getPlanKey());
      }
      planMapper.updateById(entity);
    }

    // 转换回聚合 (包含生成的 ID/version)
    return planConverter.toAggregate(entity);
  }

  /// 通过 planKey 查找 plan (幂等查询)
  @Override
  public Optional<PlanAggregate> findByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return Optional.empty();
    }

    PlanDO entity = planMapper.findByPlanKey(planKey);
    boolean found = entity != null;

    if (log.isDebugEnabled()) {
      log.debug("通过 planKey={} 查询 plan, found={}", planKey, found);
    }

    return Optional.ofNullable(entity)
        .map(planConverter::toAggregate);
  }

  /// 检查 planKey 是否存在
  @Override
  public boolean existsByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return false;
    }
    return planMapper.countByPlanKey(planKey) > 0;
  }
}
```

**核心要点**:
- ✅ 实现 domain 端口接口
- ✅ 使用 MapStruct 进行 DO ↔ Domain 转换
- ✅ 使用 MyBatis-Plus 进行简单操作
- ✅ 在 DEBUG 级别记录详细日志
- ❌ 永远不要在 infrastructure 层之外暴露 DOs

---

#### MyBatis-Plus Mapper: PlanMapper

**文件**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/mapper/PlanMapper.java`

```java
/// Plan 实体的 MyBatis-Plus mapper。
/// 
/// 扩展 BaseMapper 以进行 CRUD 操作。
/// 自定义查询在此处或 XML mapper 中定义。
@Mapper
public interface PlanMapper extends BaseMapper<PlanDO> {

  /// 通过业务键查找 plan
/// @param planKey 幂等键
/// @return 如果找到则返回 plan DO
  PlanDO findByPlanKey(@Param("planKey") String planKey);

  /// 按业务键计数 plans (用于存在性检查)
/// @param planKey 幂等键
/// @return 计数 (0 或 1)
  int countByPlanKey(@Param("planKey") String planKey);
}
```

---

#### Data Object: PlanDO

**文件**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/entity/PlanDO.java`

```java
/// t_batch_plan 表的 MyBatis-Plus 数据对象。
/// 
/// 注解说明:
/// - @TableName: 映射到表名
/// - @TableId: 自动生成 ID
/// - @TableField: 自定义类型处理器或列映射
/// - @TableLogic: 逻辑删除
/// - @Version: 乐观锁
@Data
@TableName("t_batch_plan")
public class PlanDO {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long scheduleInstanceId;
  private String planKey;
  private String provenanceCode;
  private String operationCode;

  /// 计划表达式原型的哈希值
  private String exprProtoHash;

  /// 表达式原型的 JSON 快照
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String exprProtoSnapshotJson;

  /// Provenance 配置的 JSON 快照
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String provenanceConfigSnapshotJson;

  private String provenanceConfigHash;

  /// 时间窗口规范 (JSON)
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String windowSpecJson;

  /// 切片策略代码
  private String sliceStrategyCode;

  /// 切片参数 (JSON)
  @TableField(typeHandler = JacksonTypeHandler.class)
  private String sliceParamsJson;

  /// 当前计划状态
  private Integer status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;

  /// 逻辑删除标志
  @TableLogic
  private Boolean deleted;

  /// 乐观锁版本号
  @Version
  private Integer version;
}
```

**关键注解**:
- `@TableName`: 映射到表名
- `@TableId(type = IdType.ASSIGN_ID)`: 自动生成 ID (Snowflake)
- `@TableField(typeHandler = JacksonTypeHandler.class)`: 存储 JSON
- `@TableLogic`: 逻辑删除 (deleted=true)
- `@Version`: 乐观锁
- `@TableField(fill = FieldFill.INSERT)`: 插入时自动填充

---

#### MapStruct Converter: PlanConverter

**文件**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/converter/PlanConverter.java`

```java
/// PlanAggregate 和 PlanDO 之间的 MapStruct 转换器。
/// 
/// 处理:
/// - 值对象 ↔ 基本类型转换
/// - 枚举 ↔ 代码转换
/// - 复杂对象 ↔ JSON 转换
@Mapper(componentModel = "spring")
public interface PlanConverter {

  /// 将 DO 转换为 Domain 聚合根
/// 
/// @param entity 来自数据库的 PlanDO
/// @return 用于领域层的 PlanAggregate
  @Mapping(target = "id", source = "id")
  @Mapping(target = "scheduleInstanceId", source = "scheduleInstanceId")
  @Mapping(target = "planKey", source = "planKey")
  @Mapping(target = "provenanceCode", source = "provenanceCode")
  @Mapping(target = "operationCode",
      expression = "java(mapOperationCode(entity.getOperationCode()))")
  @Mapping(target = "windowSpec",
      expression = "java(mapWindowSpec(entity.getWindowSpecJson()))")
  @Mapping(target = "status",
      expression = "java(mapPlanStatus(entity.getStatus()))")
  PlanAggregate toAggregate(PlanDO entity);

  /// 将 Domain 聚合根转换为 DO
/// 
/// @param aggregate 来自领域的 PlanAggregate
/// @return 用于持久化的 PlanDO
  @Mapping(target = "id", source = "id")
  @Mapping(target = "scheduleInstanceId", source = "scheduleInstanceId")
  @Mapping(target = "planKey", source = "planKey")
  @Mapping(target = "provenanceCode", source = "provenanceCode")
  @Mapping(target = "operationCode",
      expression = "java(aggregate.getOperationCode().getCode())")
  @Mapping(target = "windowSpecJson",
      expression = "java(serializeWindowSpec(aggregate.getWindowSpec()))")
  @Mapping(target = "status",
      expression = "java(aggregate.getStatus().getCode())")
  @Mapping(target = "deleted", ignore = true)  // 由 MyBatis-Plus 管理
  @Mapping(target = "version", ignore = true)  // 由 @Version 管理
  @Mapping(target = "createdAt", ignore = true)  // 自动填充
  @Mapping(target = "updatedAt", ignore = true)  // 自动填充
  PlanDO toEntity(PlanAggregate aggregate);

  // 复杂转换的辅助方法
  default OperationCode mapOperationCode(String code) {
    return code == null ? null : OperationCode.fromCode(code);
  }

  default PlanStatus mapPlanStatus(Integer code) {
    return code == null ? null : PlanStatus.fromCode(code);
  }

  default WindowSpec mapWindowSpec(String json) {
    // JSON 反序列化逻辑
    return WindowSpec.fromJson(json);
  }

  default String serializeWindowSpec(WindowSpec spec) {
    // JSON 序列化逻辑
    return spec.toJson();
  }
}
```

**关键要点**:
- ✅ 使用 MapStruct 进行类型安全的转换
- ✅ 使用表达式映射处理复杂转换
- ✅ 忽略自动管理的字段 (version, timestamps)
- ✅ 使用辅助方法处理枚举/JSON 转换

---

## 重构示例: 从错误到正确

### 重构前: 业务逻辑在错误的层 ❌

```java
// ❌ 错误: 在 Application 层直接使用 MyBatis-Plus
@Service
@RequiredArgsConstructor
public class PlanIngestionService {

  private final PlanMapper planMapper;  // ❌ 错误! 应该使用领域端口

  @Transactional
  public void createPlan(CreatePlanRequest request) {
    // ❌ 业务逻辑与持久化混合
    PlanDO planDO = new PlanDO();
    planDO.setProvenanceCode(request.getProvenanceCode());
    planDO.setOperationCode(request.getOperationCode());

    // ❌ 业务规则在服务层
    if (request.getWindowFrom().isAfter(request.getWindowTo())) {
      throw new ValidationException("Invalid window");
    }

    // ❌ 直接使用 mapper
    planMapper.insert(planDO);

    // ... 还有 100 多行混合逻辑
  }
}
```

### 重构后: 清晰的分层 ✅

**1. Domain 层** (业务规则):
```java
// ✅ 正确: 业务规则在领域聚合根中
@Getter
public class PlanAggregate extends AggregateRoot<Long> {

  private final WindowSpec windowSpec;

  // 带验证的工厂方法
  public static PlanAggregate create(/* parameters */) {
    // ✅ 业务规则验证在领域层
    if (windowFrom.isAfter(windowTo)) {
      throw new IllegalArgumentException("Invalid window: from must be before to");
    }
    return new PlanAggregate(/* ... */);
  }
}
```

**2. Application 层** (编排):
```java
// ✅ 正确: 清晰的编排,委托给领域和端口
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

  private final PlanRepository planRepository;  // ✅ 领域端口
  private final PlanPersistenceCoordinator persistenceCoordinator;

  @Transactional
  public PlanIngestionResult ingestPlan(PlanIngestionCommand command) {
    // ✅ 委托给领域进行创建
    PlanAggregate plan = PlanAggregate.create(/* ... */);

    // ✅ 委托给协调器进行持久化
    PlanAggregate persisted = persistenceCoordinator.savePlan(plan);

    return PlanIngestionResult.from(persisted);
  }
}
```

**3. Infrastructure 层** (数据访问):
```java
// ✅ 正确: 清晰的仓储实现
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

  private final PlanMapper planMapper;
  private final PlanConverter planConverter;

  @Override
  public PlanAggregate save(PlanAggregate plan) {
    // ✅ 转换 domain → DO
    PlanDO entity = planConverter.toEntity(plan);
    planMapper.insert(entity);

    // ✅ 转换回 DO → domain
    return planConverter.toAggregate(entity);
  }
}
```

**结果**:
- Domain: 纯业务逻辑,易于测试
- Application: 清晰的编排,无业务规则
- Infrastructure: 清晰的数据访问,无业务逻辑
- **可测试、可维护、遵循架构原则!**

---

## 端到端请求流程

### 完整请求流程图

```
调度器触发 (XXL-Job 或手动)
    ↓
PlanIngestionOrchestrator.ingestPlan(command)
    ↓
阶段 1: 准备规划上下文
    ├─ PlanPersistenceCoordinator.persistScheduleInstance()
    │   └─ ScheduleInstanceRepositoryMpImpl.saveOrUpdateInstance()
    │       └─ MyBatis-Plus insert/update
    ├─ PatraRegistryPort.fetchConfig() [外部服务调用]
    ├─ CursorRepository.findLatestGlobalTimeWatermark()
    │   └─ MyBatis-Plus query
    └─ PlanningWindowResolver.resolveWindow() [领域服务]
    ↓
阶段 2: 构建计划表达式
    └─ PlanExpressionBuilder.build() [领域服务]
    ↓
阶段 3: 预验证
    ├─ TaskRepository.countQueuedTasks()
    │   └─ MyBatis-Plus count query
    └─ PlannerValidator.validateBeforeAssemble() [领域服务]
    ↓
阶段 4: 组装计划
    └─ PlanAssembler.assemble()
        └─ 返回 PlanAggregate + List<PlanSliceAggregate> + List<TaskAggregate>
    ↓
阶段 5: 检查幂等性
    └─ PlanRepository.findByPlanKey()
        └─ MyBatis-Plus 按业务键查询
        ↓
        如果存在 → PlanIdempotencyCoordinator.handleIdempotentPlanReuse()
        如果不存在 → 继续阶段 6
    ↓
阶段 6: 持久化和发布
    ├─ PlanPersistenceCoordinator.savePlan()
    │   └─ PlanRepositoryMpImpl.save()
    │       └─ MyBatis-Plus insert
    ├─ PlanPersistenceCoordinator.persistSlices()
    │   └─ PlanSliceRepositoryMpImpl.saveAll()
    │       └─ MyBatis-Plus batch insert
    ├─ PlanPersistenceCoordinator.persistTasks()
    │   └─ TaskRepositoryMpImpl.saveAll()
    │       └─ MyBatis-Plus batch insert
    ├─ PlanPublishingCoordinator.collectQueuedEvents()
    │   └─ 为每个任务创建 TaskQueuedEvent
    └─ PlanPublishingCoordinator.publishNewPlanEvents()
        └─ OutboxPublisher.publish() [Outbox 模式, 与 DB 事务原子性]
            └─ OutboxMessageRepositoryMpImpl.insert()
                └─ MyBatis-Plus insert into t_outbox_message
    ↓
@Transactional COMMIT (所有持久化 + outbox 在单个事务中)
    ↓
Outbox 中继任务 (独立进程)
    └─ 从 t_outbox_message 发布消息到 RocketMQ
    ↓
任务执行工作节点
    └─ 消费 TaskQueuedEvent 并执行数据采集
```

**关键观察**:
- ✅ **单个 @Transactional 边界** 在编排器层级
- ✅ **Outbox 模式** 确保可靠的事件发布
- ✅ **幂等性** 通过业务键 (planKey)
- ✅ **关注点分离** 通过协调器
- ✅ **事务内无外部 API 调用** (配置获取在事务之前)

---

## 测试策略

### 1. Domain 层测试 (纯 Java)

**文件**: `patra-ingest-domain/src/test/java/com/patra/ingest/domain/model/aggregate/PlanAggregateTest.java`

```java
/// PlanAggregate 的单元测试 (不需要 mock)
class PlanAggregateTest {

  @Test
  void should_create_plan_with_valid_window() {
    // Given
    Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
    Instant windowTo = Instant.parse("2024-01-02T00:00:00Z");

    // When
    PlanAggregate plan = PlanAggregate.create(
        scheduleInstanceId,
        planKey,
        provenanceCode,
        operationCode,
        windowFrom,
        windowTo,
        /* other params */
    );

    // Then
    assertThat(plan).isNotNull();
    assertThat(plan.getWindowSpec().from()).isEqualTo(windowFrom);
    assertThat(plan.getWindowSpec().to()).isEqualTo(windowTo);
  }

  @Test
  void should_throw_exception_when_window_invalid() {
    // Given
    Instant windowFrom = Instant.parse("2024-01-02T00:00:00Z");
    Instant windowTo = Instant.parse("2024-01-01T00:00:00Z");  // 在 from 之前!

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> {
      PlanAggregate.create(/* params with invalid window */);
    });
  }
}
```

### 2. Application 层测试 (Mock 端口)

**文件**: `patra-ingest-app/src/test/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestratorTest.java`

```java
/// PlanIngestionOrchestrator 的单元测试 (mock 领域端口)
@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {

  @Mock
  private PlanRepository planRepository;

  @Mock
  private CursorRepository cursorRepository;

  @Mock
  private PlanPersistenceCoordinator persistenceCoordinator;

  @InjectMocks
  private PlanIngestionOrchestrator orchestrator;

  @Test
  void should_create_plan_successfully() {
    // Given
    PlanIngestionCommand command = createTestCommand();
    when(cursorRepository.findLatestGlobalTimeWatermark(any(), any()))
        .thenReturn(Optional.of(Instant.now()));
    when(planRepository.findByPlanKey(any()))
        .thenReturn(Optional.empty());
    when(persistenceCoordinator.savePlan(any()))
        .thenReturn(createTestPlan());

    // When
    PlanIngestionResult result = orchestrator.ingestPlan(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.planId()).isNotNull();
    verify(persistenceCoordinator).savePlan(any());
  }
}
```

### 3. Infrastructure 层测试 (使用 TestContainers 集成)

**文件**: `patra-ingest-boot/src/test/java/com/patra/ingest/infra/persistence/repository/PlanRepositoryMpImplIT.java`

```java
/// PlanRepositoryMpImpl 的集成测试 (真实数据库)
@SpringBootTest
@Testcontainers
class PlanRepositoryMpImplIT {

  @Container
  static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
      .withDatabaseName("patra_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private PlanRepository planRepository;

  @Test
  void should_save_and_find_plan_by_plan_key() {
    // Given
    PlanAggregate plan = createTestPlan();
    String planKey = plan.getPlanKey();

    // When
    PlanAggregate saved = planRepository.save(plan);
    Optional<PlanAggregate> found = planRepository.findByPlanKey(planKey);

    // Then
    assertThat(saved.getId()).isNotNull();
    assertThat(found).isPresent();
    assertThat(found.get().getPlanKey()).isEqualTo(planKey);
  }

  @Test
  void should_handle_duplicate_plan_key_idempotently() {
    // Given
    PlanAggregate plan1 = createTestPlan();
    PlanAggregate plan2 = createTestPlan();  // 相同的 planKey

    // When
    planRepository.save(plan1);
    boolean exists = planRepository.existsByPlanKey(plan1.getPlanKey());

    // Then
    assertThat(exists).isTrue();
    // 尝试保存 plan2 会违反 planKey 的唯一约束
  }
}
```

---

**相关文件:**
- [SKILL.md](../SKILL.md) - 主要指南
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Orchestrator 模式
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - Domain 层模式
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - 数据库访问模式
- [adapter-layer-patterns.md](adapter-layer-patterns.md) - Adapter 层模式
