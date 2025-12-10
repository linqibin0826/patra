# 适配器层模式

## 决策矩阵

**我应该创建什么类型的适配器?**

| 触发源 | 适配器类型 | 包路径 | 命名模式 | 继承/注解 | 配套类 |
|--------|----------|--------|----------|----------|--------|
| 调度任务 | XXL-Job | `adapter/scheduler/job/` | `*Job.java` | 继承 `AbstractProvenanceScheduleJob` | - |
| HTTP 请求 | REST API | `adapter/rest/` | `*Controller.java` | `@RestController` + `@Validated` | `*ApiConverter` (MapStruct) |
| 消息队列 | MQ 消费者 | `adapter/stream/` | `*MessageListener.java` | `@RocketMQMessageListener` | - |

## 核心原则

### 原则 1: 薄适配器，委托给 CommandBus

```java
// ✅ 良好：薄适配器
@XxlJob("outboxRelay")
public void execute() {
  OutboxRelayCommand command = buildCommand(parseParam(XxlJobHelper.getJobParam()));
  RelayReport report = commandBus.handle(command);
  XxlJobHelper.handleSuccess(formatReport(report));
}
```

```java
// ❌ 错误：适配器包含业务逻辑
@XxlJob("outboxRelay")
public void execute() {
  List<OutboxMessage> messages = outboxRepository.fetchPending(...);  // ❌ 直接仓储访问
  for (OutboxMessage msg : messages) {
    if (msg.getRetryCount() < 3) {  // ❌ 业务规则
      publisher.publish(msg);
    }
  }
}
```

### 原则 2: 适配器职责清单

**✅ 适配器层应该:**
- 接收外部请求（HTTP、作业、MQ）
- 验证输入参数（`@Valid`、`@NotNull` 等）
- **通过 Converter 转换**：Request → Command（反腐层）
- 委托给 CommandBus（写操作）或 QueryService（查询操作）
- **通过 Converter 转换**：Result → Response（反腐层）
- 处理适配器特定错误报告（如 XXL-Job 状态）

**❌ 适配器层不应该:**
- 包含业务逻辑
- 直接访问领域仓储
- 直接调用基础设施层
- 实现重试逻辑（委托给 Handler）
- **直接传递 Request/Response 给 Application 层**（必须通过 Converter 转换）

## XXL-Job 模式

### 快速启动（3 步创建定时任务）

```java
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  // 步骤 1: 声明数据源和操作
  @Override
  protected ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  protected OperationCode getOperationCode() {
    return OperationCode.HARVEST;
  }

  // 步骤 2: 定义 XXL-Job 入口点
  @XxlJob("pubmedHarvest")
  public void run() {
    executeScheduleJob(XxlJobHelper.getJobParam());
  }
}
```

✅ **完成！** 约 20 行代码实现一个完整的定时任务。

### 模板方法模式（Template Method Pattern）

**抽象基类提供通用逻辑**：

```java
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

  @Autowired private CommandBus commandBus;

  protected abstract ProvenanceCode getProvenanceCode();
  protected abstract OperationCode getOperationCode();

  /// 通用作业执行流程: 解析参数 → 调用 CommandBus → 报告结果
  protected void executeScheduleJob(String paramStr) {
    try {
      PlanIngestionCommand command = parseJobParam(paramStr);
      PlanIngestionResult result = commandBus.handle(command);

      // 成功：记录日志并报告
      log.info("作业完成: provenance={} planId={}", getProvenanceCode(), result.planId());
      XxlJobHelper.handleSuccess("创建计划: planId=" + result.planId());
    } catch (Exception e) {
      // 失败：记录错误并报告
      log.error("作业失败: provenance={} error={}", getProvenanceCode(), e.getMessage(), e);
      XxlJobHelper.handleFail("作业失败: " + e.getMessage());
      // 不再抛出异常，handleFail 已标记任务失败，避免堆栈覆盖友好消息
    }
  }
}
```

**优势**：
- ✅ 每个具体作业约 20 行（最少样板代码）
- ✅ 所有作业间一致的错误处理
- ✅ 易于添加新的来源/操作作业

## REST 控制器模式

### 快速启动（创建 REST API）

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
@Validated
public class ProvenanceController {

    private final CommandBus commandBus;              // 写操作
    private final ProvenanceQueryService queryService; // 查询操作
    private final ProvenanceApiConverter converter;    // MapStruct 转换器

    /// 创建 Provenance 配置
    @PostMapping
    public ProvenanceResponse create(@Valid @RequestBody CreateProvenanceRequest request) {
        log.info("收到创建 Provenance 请求，代码：{}", request.code());

        // 1. Request → Command（反腐层）
        CreateProvenanceCommand command = converter.toCommand(request);

        // 2. 调用 CommandBus（写操作）
        ProvenanceResult result = commandBus.handle(command);

        // 3. Result → Response
        return converter.toResponse(result);
    }

    /// 查询 Provenance 配置
    @GetMapping("/{code}")
    public ProvenanceResponse getByCode(@PathVariable @NotNull String code) {
        ProvenanceQuery result = queryService.findByCode(code);
        return converter.toResponse(result);
    }

    /// 内部 DTO（使用 record）
    public record CreateProvenanceRequest(
        @NotBlank(message = "代码不能为空") String code,
        @NotBlank(message = "名称不能为空") String name,
        @NotNull(message = "类型不能为空") String type
    ) {}

    public record ProvenanceResponse(
        Long id,
        String code,
        String name,
        String type
    ) {}
}
```

**关键要点**：
- ✅ 直接返回业务数据（不使用 ResponseEntity）
- ✅ 使用 Converter 进行 Request → Command → Result → Response 转换
- ✅ Request/Response 是独立的 DTO（record 类型）
- ✅ 异常由全局处理器处理（不在 Controller 层 try-catch）

### MapStruct Converter（反腐层）

```java
package com.patra.catalog.adapter.rest.converter;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// Provenance API 转换器（反腐层）
///
/// - Request/Response 属于 Adapter 层（外部协议）
/// - Command/Result 属于 Application 层（应用逻辑）
/// - Converter 隔离外部协议和应用逻辑
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceApiConverter {

  /// 将创建请求转换为创建命令
  CreateProvenanceCommand toCommand(CreateProvenanceRequest request);

  /// 将应用层结果转换为响应 DTO
  ProvenanceResponse toResponse(ProvenanceResult result);

  /// 将应用层结果列表转换为响应 DTO 列表
  List<ProvenanceResponse> toResponse(List<ProvenanceResult> results);
}
```

## 实施指南

### XXL-Job 错误处理

```java
// ✅ 良好：向调度器报告错误，不再抛出异常
try {
  RelayReport report = commandBus.handle(command);
  XxlJobHelper.handleSuccess("成功中继 " + report.count() + " 条消息");
} catch (Exception ex) {
  log.error("中继失败", ex);
  XxlJobHelper.handleFail("中继失败: " + ex.getMessage());
  // 不再抛出异常，handleFail 已标记任务失败
  // 抛出异常会导致 XXL-Job 用堆栈覆盖 handleFail 设置的友好消息
}
```

```java
// ❌ 错误：既不报告也不记录
try {
  commandBus.handle(command);
} catch (Exception ex) {
  // ❌ 不向调度器报告，作业看起来成功了！
  // ❌ 不记录日志，无法排查问题！
}
```

### XXL-Job 配置外部化

```java
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {
  private final OutboxRelayProperties properties;  // 来自 Nacos
  private final CommandBus commandBus;

  @XxlJob("outboxRelay")
  public void execute() {
    if (!properties.isEnabled()) {
      XxlJobHelper.handleSuccess("中继已禁用");
      return;
    }

    OutboxRelayCommand command = buildCommandFromProperties(properties);
    RelayReport report = commandBus.handle(command);
    XxlJobHelper.handleSuccess("成功中继 " + report.count() + " 条消息");
  }
}
```

## 常见反模式

### ❌ 反模式 1: 适配器中的业务逻辑

```java
// ❌ 错误：适配器包含业务规则
@XxlJob("taskExecution")
public void execute() {
  List<Task> tasks = taskRepository.findReady();
  for (Task task : tasks) {
    if (task.getRetryCount() < 3 && task.getStatus() == TaskStatus.PENDING) {  // ❌ 业务逻辑
      taskExecutor.execute(task);
    }
  }
}

// ✅ 正确：委托给 CommandBus
@XxlJob("taskExecution")
public void execute() {
  TaskExecutionCommand command = parseCommand(XxlJobHelper.getJobParam());
  TaskExecutionResult result = commandBus.handle(command);
  XxlJobHelper.handleSuccess(formatResult(result));
}
```

### ❌ 反模式 2: 直接仓储访问

```java
// ❌ 错误：适配器绕过应用层
@XxlJob("cleanupOldData")
public void execute() {
  outboxRepository.deleteOlderThan(cutoff);  // ❌ 直接访问基础设施层
}

// ✅ 正确：使用 CommandBus
@XxlJob("cleanupOldData")
public void execute() {
  CleanupCommand command = new CleanupCommand(Instant.now().minus(Duration.ofDays(90)));
  CleanupResult result = commandBus.handle(command);
  XxlJobHelper.handleSuccess("已清理 " + result.count() + " 条记录");
}
```

### ❌ 反模式 3: Controller 错误模式

```java
// ❌ 错误 1：使用 ResponseEntity 包装
public ResponseEntity<ProvenanceResponse> create(@RequestBody CreateProvenanceRequest request) {
  return ResponseEntity.ok(converter.toResponse(result));
}

// ❌ 错误 2：在 Controller 捕获异常
public ProvenanceResponse create(@RequestBody CreateProvenanceRequest request) {
  try {
    return converter.toResponse(useCase.create(converter.toCommand(request)));
  } catch (Exception e) {  // ❌ 全局处理器已统一处理
    throw new RuntimeException("创建失败");
  }
}

// ❌ 错误 3：直接传递 Request 给 Application 层
public ProvenanceResponse create(@RequestBody CreateProvenanceRequest request) {
  return converter.toResponse(useCase.create(request));  // ❌ Request 不应传入 Application 层
}

// ✅ 正确：直接返回业务数据 + Converter 转换
public ProvenanceResponse create(@Valid @RequestBody CreateProvenanceRequest request) {
  CreateProvenanceCommand command = converter.toCommand(request);
  ProvenanceResult result = useCase.create(command);
  return converter.toResponse(result);
}
```

## 最佳实践速查表

### ✅ 应该

| 实践 | 原因 | 适用 |
|------|------|------|
| **委托给 CommandBus/QueryService** | 保持适配器薄，业务逻辑在应用层 | 全部 |
| **使用 Converter 转换** | Request → Command → Result → Response（反腐层） | Controller |
| **直接返回业务数据** | 不使用 ResponseEntity 包装 | Controller |
| **向调度器报告** | 使用 `XxlJobHelper.handleSuccess/Fail()` 提高可见性 | XXL-Job |
| **使用模板模式** | 将通用逻辑提取到抽象基类 | XXL-Job |
| **外部化配置** | 使用 Nacos/环境变量，支持功能开关 | 全部 |

### ❌ 不应该

| 反模式 | 问题 | 适用 |
|--------|------|------|
| **适配器中的业务逻辑** | 违反分层架构，难以测试 | 全部 |
| **直接仓储访问** | 绕过事务边界和业务规则 | 全部 |
| **直接传递 Request/Command** | 破坏反腐层，外部协议污染应用层 | Controller |
| **使用 ResponseEntity 包装** | 冗余，直接返回业务数据即可 | Controller |
| **在 Controller 捕获异常** | 全局处理器已统一处理 | Controller |
| **吞掉异常** | 调度器无法检测失败，无重试 | XXL-Job |
