# 适配器层模式

**目的**: 驱动适配器接收外部触发(HTTP、作业、MQ)并委托给应用层编排者。

---

## 目录

1. [概览](#概览)
2. [XXL-Job 模式](#xxl-job-模式)
3. [核心原则](#核心原则)
4. [反模式](#反模式)
5. [最佳实践](#最佳实践)

---

## 概览

### 适配器层职责

**适配器层**(驱动适配器)处理外部触发并将其转换为应用层调用。

**✅ 适配器层应该:**
- 接收外部请求(HTTP、作业、MQ)
- 解析/验证输入参数
- 委托给用例编排者
- 将领域结果映射到适配器特定响应
- 处理适配器特定错误报告

**❌ 适配器层不应该:**
- 包含业务逻辑
- 直接访问领域仓储
- 直接调用基础设施层
- 实现重试逻辑(委托给编排者)

---

## XXL-Job 模式

### 概览

Patra 使用 **XXL-Job** 进行分布式定时任务。适配器层提供作业入口点。

### 模式: 模板方法(抽象基础作业)

**问题**: 多个作业共享通用逻辑(参数解析、错误处理、指标)

**解决方案**: 将通用逻辑提取到抽象基类

**文件**: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/AbstractProvenanceScheduleJob.java`

```java
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

  @Autowired private PlanIngestionUseCase planIngestionUseCase;
  @Autowired private ObjectMapper objectMapper;

  // ✅ 模板方法: 子类提供来源/操作
  protected abstract ProvenanceCode getProvenanceCode();
  protected abstract OperationCode getOperationCode();

  /**
   * 通用作业执行流程: 解析参数 → 调用编排者 → 报告结果
   */
  protected void executeScheduleJob(String paramStr) {
    long startTime = System.currentTimeMillis();

    try {
      // ✅ 解析 XXL-Job JSON 参数
      PlanIngestionCommand command = parseJobParam(paramStr);

      // ✅ 委托给编排者
      PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

      // ✅ 向 XXL-Job 报告成功
      handleJobSuccess(result, startTime);
    } catch (Exception e) {
      // ✅ 向 XXL-Job 报告失败
      handleJobFailure(e, startTime);
      throw e;  // 让 XXL-Job 处理重试
    }
  }

  private PlanIngestionCommand parseJobParam(String paramStr) {
    if (CharSequenceUtil.isBlank(paramStr)) {
      return buildDefaultCommand();
    }

    Map<String, Object> rawParams = objectMapper.readValue(paramStr, new TypeReference<>() {});
    ProvenanceScheduleJobParam jobParam = objectMapper.convertValue(rawParams, ProvenanceScheduleJobParam.class);

    return new PlanIngestionCommand(
        getProvenanceCode(),
        getOperationCode(),
        jobParam.step(),
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        String.valueOf(XxlJobHelper.getJobId()),
        // ... 其他字段
    );
  }

  private void handleJobSuccess(PlanIngestionResult result, long startTime) {
    long elapsed = System.currentTimeMillis() - startTime;

    log.info(
        "作业完成: provenance={} operation={} planId={} costMs={}",
        getProvenanceCode(), getOperationCode(), result.planId(), elapsed);

    // ✅ 报告到 XXL-Job 控制台
    XxlJobHelper.handleSuccess(
        String.format("创建计划: planId=%d, slices=%d", result.planId(), result.sliceCount()));
  }

  private void handleJobFailure(Exception e, long startTime) {
    long elapsed = System.currentTimeMillis() - startTime;

    log.error(
        "作业失败: provenance={} operation={} error={} costMs={}",
        getProvenanceCode(), getOperationCode(), e.getMessage(), elapsed, e);

    // ✅ 报告到 XXL-Job 控制台
    XxlJobHelper.handleFail("作业失败: " + e.getMessage());
  }
}
```

### 具体作业实现

**文件**: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/PubmedHarvestJob.java`

```java
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  // ✅ 声明固定来源
  @Override
  protected ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  // ✅ 声明固定操作
  @Override
  protected OperationCode getOperationCode() {
    return OperationCode.HARVEST;
  }

  /**
   * XXL-Job 入口点: 获取参数并委托给基类
   */
  @XxlJob("pubmedHarvest")
  public void run() {
    String jobParam = XxlJobHelper.getJobParam();
    log.debug("PubMed 采集作业触发,参数: {}", jobParam);

    // ✅ 委托给模板方法
    executeScheduleJob(jobParam);
  }
}
```

**优势:**
- ✅ 每个具体作业约20行(最少样板代码)
- ✅ 所有作业间一致的错误处理
- ✅ 易于添加新的来源/操作作业
- ✅ 可测试(在基类中注入模拟编排者)

---

## 核心原则

### 1. 薄适配器,委托给编排者

```java
// ✅ 良好: 适配器立即委托
@XxlJob("outboxRelay")
public void execute() {
  OutboxRelayJobParam param = parseParam(XxlJobHelper.getJobParam());

  OutboxRelayCommand command = buildCommand(param, Instant.now());

  // ✅ 委托给编排者
  RelayReport report = relayUseCase.relay(command);

  // ✅ 报告统计
  XxlJobHelper.handleSuccess(formatReport(report));
}
```

```java
// ❌ 错误: 适配器中的业务逻辑
@XxlJob("outboxRelay")
public void execute() {
  List<OutboxMessage> messages = outboxRepository.fetchPending(...);  // ❌ 直接仓储访问

  for (OutboxMessage msg : messages) {
    if (msg.getRetryCount() < 3) {  // ❌ 适配器中的业务规则
      publisher.publish(msg);
    }
  }
}
```

### 2. 参数解析和验证

```java
// ✅ 良好: 在适配器中解析,在领域中验证
private OutboxRelayCommand buildCommand(OutboxRelayJobParam param, Instant now) {
  return new OutboxRelayCommand(
      resolveChannel(param.channel()),
      now,
      param.batchSize(),
      parseDuration(param.leaseDuration()),
      param.maxAttempts(),
      parseDuration(param.initialBackoff()),
      buildLeaseOwner()  // ✅ 适配器特定(host+jobId+threadId)
  );
}

private String buildLeaseOwner() {
  String host = NetUtil.getLocalHostName();
  return host + '-' + XxlJobHelper.getJobId() + '-'
      + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
}
```

### 3. 错误处理和报告

```java
// ✅ 良好: 向调度器报告错误
try {
  RelayReport report = relayUseCase.relay(command);
  XxlJobHelper.handleSuccess(formatSuccessMessage(report));
} catch (OutboxRelayExecutionException ex) {
  log.error("中继执行失败: {}", ex.getMessage(), ex);
  XxlJobHelper.handleFail("中继失败: " + ex.getMessage());
  throw ex;  // ✅ 让 XXL-Job 重试策略决定
}
```

```java
// ❌ 错误: 吞掉异常
try {
  relayUseCase.relay(command);
} catch (Exception ex) {
  log.error("错误: {}", ex.getMessage());
  // ❌ 不向调度器报告,作业看起来成功了!
}
```

### 4. 幂等作业执行

```java
// ✅ 良好: 每次执行生成唯一租约拥有者
private String buildLeaseOwner() {
  // host-jobId-threadId-uuid 确保唯一性
  String host = NetUtil.getLocalHostName();
  return host + '-' + XxlJobHelper.getJobId() + '-'
      + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
}
```

**原因:**
- 多个作业实例可能并发运行
- 租约拥有者标识哪个实例拥有消息
- UUID 防止作业快速重启时冲突

---

## 反模式

### ❌ 适配器中的业务逻辑

```java
// ❌ 错误: 适配器包含业务规则
@XxlJob("taskExecution")
public void execute() {
  List<Task> tasks = taskRepository.findReady();

  for (Task task : tasks) {
    // ❌ 业务逻辑: 重试计算、状态验证
    if (task.getRetryCount() < 3 && task.getStatus() == TaskStatus.PENDING) {
      taskExecutor.execute(task);
      task.setStatus(TaskStatus.RUNNING);
      taskRepository.save(task);
    }
  }
}
```

```java
// ✅ 良好: 委托给编排者
@XxlJob("taskExecution")
public void execute() {
  TaskExecutionCommand command = parseCommand(XxlJobHelper.getJobParam());

  // ✅ 编排者处理所有业务逻辑
  TaskExecutionResult result = taskExecutionUseCase.execute(command);

  XxlJobHelper.handleSuccess(formatResult(result));
}
```

### ❌ 直接仓储访问

```java
// ❌ 错误: 适配器绕过应用层
@XxlJob("cleanupOldData")
public void execute() {
  Instant cutoff = Instant.now().minus(Duration.ofDays(90));

  // ❌ 直接访问基础设施层
  outboxRepository.deleteOlderThan(cutoff);
  taskRepository.deleteOlderThan(cutoff);
}
```

```java
// ✅ 良好: 使用清理编排者
@XxlJob("cleanupOldData")
public void execute() {
  CleanupCommand command = new CleanupCommand(
      Instant.now().minus(Duration.ofDays(90)));

  // ✅ 编排者协调跨聚合的清理
  CleanupResult result = cleanupUseCase.cleanup(command);

  XxlJobHelper.handleSuccess(
      String.format("已清理 %d 个 outbox, %d 个任务",
          result.outboxDeleted(), result.tasksDeleted()));
}
```

### ❌ 缺少错误报告

```java
// ❌ 错误: 不向调度器报告错误
@XxlJob("importData")
public void execute() {
  try {
    importUseCase.importData(...);
  } catch (Exception ex) {
    log.error("导入失败", ex);
    // ❌ XXL-Job 认为作业成功了!
  }
}
```

```java
// ✅ 良好: 正确报告错误
@XxlJob("importData")
public void execute() {
  try {
    ImportResult result = importUseCase.importData(...);
    XxlJobHelper.handleSuccess("已导入: " + result.count());
  } catch (Exception ex) {
    log.error("导入失败", ex);
    XxlJobHelper.handleFail("导入失败: " + ex.getMessage());
    throw ex;  // ✅ 让 XXL-Job 重试
  }
}
```

---

## 最佳实践

### ✅ 应该

| 实践 | 原因 |
|----------|--------|
| **委托给编排者** | 保持适配器薄,业务逻辑在应用层 |
| **在适配器中解析参数** | 适配器特定格式(JSON、环境变量等) |
| **向调度器报告** | 使用 `XxlJobHelper.handleSuccess/Fail()` 提高可见性 |
| **使用模板模式** | 将通用逻辑提取到抽象基类 |
| **生成唯一标识符** | 租约拥有者、跟踪ID用于分布式协调 |
| **记录作业生命周期** | 启动、成功、失败及时间指标 |

### ❌ 不应该

| 反模式 | 问题 |
|--------------|---------|
| **适配器中的业务逻辑** | 违反分层架构,难以测试 |
| **直接仓储访问** | 绕过事务边界和业务规则 |
| **吞掉异常** | 调度器无法检测失败,无重试 |
| **硬编码配置** | 使用 Nacos/环境变量以提高灵活性 |
| **跳过参数验证** | 在无效输入时快速失败 |

### 配置最佳实践

```java
// ✅ 良好: 外部化配置
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  private final OutboxRelayProperties properties;  // ✅ 来自 Nacos
  private final OutboxRelayUseCase relayUseCase;

  @XxlJob("outboxRelay")
  public void execute() {
    // ✅ 检查功能开关
    if (!properties.isEnabled()) {
      log.info("Outbox 中继已禁用,跳过执行");
      XxlJobHelper.handleSuccess("中继已禁用");
      return;
    }

    // ✅ 使用配置值
    OutboxRelayCommand command = new OutboxRelayCommand(
        /* channel */ null,  // 所有通道
        Instant.now(),
        properties.getBatchSize(),        // ✅ 来自配置
        properties.getLeaseDuration(),    // ✅ 来自配置
        properties.getMaxAttempts(),      // ✅ 来自配置
        properties.getInitialBackoff(),   // ✅ 来自配置
        buildLeaseOwner()
    );

    RelayReport report = relayUseCase.relay(command);
    XxlJobHelper.handleSuccess(formatReport(report));
  }
}
```

### 测试作业

```java
// ✅ 良好: 使用模拟编排者测试基类
@ExtendWith(MockitoExtension.class)
class AbstractProvenanceScheduleJobTest {

  @Mock private PlanIngestionUseCase mockUseCase;
  @Mock private ObjectMapper mockMapper;

  private TestJob job;

  @BeforeEach
  void setUp() {
    job = new TestJob();
    ReflectionTestUtils.setField(job, "planIngestionUseCase", mockUseCase);
    ReflectionTestUtils.setField(job, "objectMapper", mockMapper);
  }

  @Test
  void should_delegate_to_orchestrator() {
    // Given
    String param = "{\"windowFrom\":\"2024-01-01T00:00:00Z\"}";
    PlanIngestionResult expectedResult = new PlanIngestionResult(123L, 10);
    when(mockUseCase.ingestPlan(any())).thenReturn(expectedResult);

    // When
    job.executeScheduleJob(param);

    // Then
    verify(mockUseCase).ingestPlan(argThat(cmd ->
        cmd.provenanceCode() == ProvenanceCode.PUBMED &&
        cmd.operationCode() == OperationCode.HARVEST
    ));
  }

  @Test
  void should_handle_orchestrator_exception() {
    // Given
    when(mockUseCase.ingestPlan(any())).thenThrow(new RuntimeException("测试错误"));

    // When/Then
    assertThrows(RuntimeException.class, () -> job.executeScheduleJob("{}"));
  }

  // 用于测试抽象基类的测试作业
  private static class TestJob extends AbstractProvenanceScheduleJob {
    @Override protected ProvenanceCode getProvenanceCode() { return ProvenanceCode.PUBMED; }
    @Override protected OperationCode getOperationCode() { return OperationCode.HARVEST; }
  }
}
```

---

**相关文件:**
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - 应用层编排
- [architecture-overview.md](architecture-overview.md) - 六边形架构概览
- [outbox-pattern.md](outbox-pattern.md) - OutboxRelayJob 实现细节

---

**📝 状态**: ✅ **完成** - 来自 patra-ingest 的适配器层模式综合指南,包含 XXL-Job 示例。
