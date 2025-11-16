# 事务与错误处理指南

> **目的**: 掌握 Spring 事务管理、异常设计和错误映射,构建健壮的微服务应用

## 🚀 快速开始

### 需要添加事务管理?

```java
// 1. 在 Orchestrator 添加事务边界
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

    private final PlanRepository planRepository;
    private final OutboxMessageRepository outboxRepository;

    @Override
    @Transactional  // ✅ 事务边界在编排者层
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        // 业务数据 + Outbox 消息原子提交
        PlanAggregate plan = assemblePlan(command);
        planRepository.save(plan);

        OutboxMessage message = createOutboxMessage(plan);
        outboxRepository.save(message);

        return new PlanCreationResult(plan.getId());
    }
}

// 2. 定义领域异常
public class InvalidPlanException extends DomainException {
    public InvalidPlanException(String message) {
        super(message);
    }
}

// 3. 在 GlobalExceptionHandler 映射到 HTTP 响应
@ExceptionHandler(InvalidPlanException.class)
public ProblemDetail handleInvalidPlan(InvalidPlanException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ex.getMessage()
    );
}
```

---

## 📊 决策矩阵

### 何时使用 @Transactional?

| 场景 | 是否使用 @Transactional | 位置 |
|------|------------------------|------|
| 编排多个仓储操作 | ✅ 是 | Orchestrator |
| 单个查询操作 | ✅ 是 (readOnly=true) | Orchestrator |
| 调用外部 API | ❌ 否 | 独立方法 |
| 仓储实现方法 | ❌ 否 | 太细粒度 |
| 领域方法 | ❌ 否 | 领域是纯 Java |
| 需要独立提交的日志 | ✅ 是 (REQUIRES_NEW) | Coordinator |

### 事务传播行为选择

```
需要什么行为?
  ├─ 加入当前事务或新建 → REQUIRED (默认)
  ├─ 总是新建独立事务 → REQUIRES_NEW
  │   └─ 用例: 审计日志、独立操作
  ├─ 必须在事务内调用 → MANDATORY
  │   └─ 用例: 内部 Coordinator
  ├─ 不需要事务 → NOT_SUPPORTED
  │   └─ 用例: 只读查询、报表
  └─ 需要部分回滚 → NESTED
      └─ 用例: 保存点场景
```

---

## 🎯 核心概念

### 为什么事务边界在 Orchestrator?

**问题**: 在错误的层级使用 @Transactional

**❌ 反模式 1: 在 Repository 层**
```java
@Repository
public class PlanRepositoryImpl implements PlanRepository {
    @Transactional  // ❌ 太细粒度,无法协调多个操作
    public void save(PlanAggregate plan) {
        // ...
    }
}
```

**❌ 反模式 2: 在 Domain 层**
```java
public class PlanAggregate {
    @Transactional  // ❌ 领域是纯 Java,不能用 Spring
    public void addSlice(PlanSlice slice) {
        // ...
    }
}
```

**✅ 正确: 在 Orchestrator 层**
```java
@Service
public class PlanIngestionOrchestrator {
    @Transactional  // ✅ 协调多个操作,自然的事务边界
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        // 所有数据库操作在同一事务中
        planRepository.save(plan);
        sliceRepository.saveAll(slices);
        outboxRepository.save(message);
    }
}
```

**原因**:
1. Orchestrator 协调用例 → 自然的事务边界
2. Domain 层是纯 Java → 不能使用 Spring 注解
3. Infrastructure 层方法太细粒度 → 无法协调多个操作

---

## 🏗️ 事务管理模式

### 模式 1: 标准事务 (REQUIRED)

<details>
<summary>查看使用场景和示例</summary>

**使用场景**: 大多数编排者,需要加入现有事务或新建事务

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

    private final PlanRepository planRepository;
    private final SliceRepository sliceRepository;

    @Transactional  // 默认 Propagation.REQUIRED
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        // 所有操作在同一事务中
        PlanAggregate plan = assemblePlan(command);
        planRepository.save(plan);

        List<PlanSlice> slices = generateSlices(plan);
        sliceRepository.saveAll(slices);

        return new PlanCreationResult(plan.getId(), slices.size());
    }
}
```

**行为**:
- 如果已存在事务 → 加入该事务
- 如果不存在事务 → 新建事务
- 任何操作失败 → 整个事务回滚

</details>

### 模式 2: 独立事务 (REQUIRES_NEW)

<details>
<summary>查看使用场景和示例</summary>

**使用场景**: 审计日志、独立操作,即使主事务回滚也要提交

```java
@Service
@RequiredArgsConstructor
public class AuditLogCoordinator {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(String operation, String details) {
        AuditLog log = AuditLog.builder()
            .operation(operation)
            .details(details)
            .timestamp(Instant.now())
            .build();
        auditLogRepository.save(log);
        // ✅ 即使调用方事务回滚,日志也会保存
    }
}
```

**使用示例**:
```java
@Transactional
public void createPlan(CreatePlanCommand command) {
    // 独立事务记录日志
    auditLogCoordinator.logOperation("CREATE_PLAN", command.toString());

    // 主业务逻辑
    PlanAggregate plan = assemblePlan(command);
    planRepository.save(plan);

    // 如果这里抛异常,计划创建回滚,但日志已提交
    if (someCondition) {
        throw new BusinessException("Plan creation failed");
    }
}
```

**行为**:
- 总是创建新事务
- 挂起当前事务 (如果存在)
- 新事务独立提交或回滚

</details>

### 模式 3: 只读事务 (readOnly=true)

<details>
<summary>查看优化和示例</summary>

**使用场景**: 查询操作,无写入需求

```java
@Transactional(readOnly = true)
public List<PlanAggregate> findActivePlans() {
    return planRepository.findByStatus(PlanStatus.RUNNING);
}

@Transactional(readOnly = true)
public PlanStatistics getStatistics(ProvenanceId provenanceId) {
    List<PlanAggregate> plans = planRepository.findByProvenance(provenanceId);
    return calculateStatistics(plans);
}
```

**优化效果**:
1. 数据库层面优化 (跳过脏检查、刷新操作)
2. 防止意外写入
3. 某些数据库可能路由到只读副本

**注意**: 如果方法内有写操作,会抛出异常

</details>

---

## ⚠️ 外部 API 调用的黄金法则

### 规则: 绝不在 @Transactional 内调用外部 API

**原因**:
1. 外部 API 调用慢 → 事务持续时间过长
2. 网络故障 → 事务超时
3. 数据库连接池耗尽
4. 无法回滚外部 API 调用

<details>
<summary>查看反模式和正确模式</summary>

**❌ 反模式: 外部 API 在事务内**
```java
@Transactional
public void harvestData(HarvestCommand command) {
    PlanAggregate plan = assemblePlan(command);
    planRepository.save(plan);  // 事务开始

    // 外部 API 调用 (慢、不可靠)
    SearchResult results = pubmedSearchPort.search(query);  // ❌ 危险!
    // 如果 PubMed API 耗时 10 秒,数据库事务持续 10+ 秒

    literatureStoragePort.saveAll(results);  // 事务仍然打开
}
```

**问题**:
- PubMed API 响应 10 秒 → 数据库事务锁定 10+ 秒
- 阻塞其他操作
- 连接池可能耗尽

**✅ 正确模式: 外部 API 在事务外**
```java
@Service
@RequiredArgsConstructor
public class HarvestOrchestrator {

    private final PlanRepository planRepository;
    private final PubmedSearchPort pubmedSearchPort;
    private final LiteratureStoragePort literatureStoragePort;

    // 事务 1: 创建计划
    @Transactional
    public PlanAggregate createPlan(CreatePlanCommand command) {
        PlanAggregate plan = assemblePlan(command);
        planRepository.save(plan);
        return plan;
    }

    // 无事务: 调用外部 API
    public SearchResult fetchFromExternalApi(PlanAggregate plan) {
        return pubmedSearchPort.search(plan.getQuery());
    }

    // 事务 2: 保存结果
    @Transactional
    public void savePublication(SearchResult results) {
        literatureStoragePort.save(results);
    }
}
```

**工作流**:
1. **事务 1**: 创建计划并提交
2. **无事务**: 调用外部 API (可能很慢)
3. **事务 2**: 保存结果

**优势**:
- 每个事务都很短
- 外部 API 故障不影响数据库
- 更好的并发性

</details>

---

## 🔧 异常设计

### 异常层次结构

```
Exception
└── RuntimeException (Unchecked)
    ├── DomainException (abstract)
    │   ├── ProvenanceNotFoundException
    │   ├── InvalidPlanException
    │   └── BusinessKeyDuplicateException
    ├── ApplicationException (abstract)
    │   ├── ProvenanceConfigNotFoundException
    │   └── OrchestrationException
    └── InfrastructureException (abstract)
        ├── RepositoryException
        └── ExternalApiException
```

### 领域异常 (Domain Exception)

<details>
<summary>查看定义和使用</summary>

**位置**: `patra-{service}-domain/src/main/java/.../exception/`

**目的**: 表示业务规则违反

**基类**:
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

**具体异常**:
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

public class InvalidPlanException extends DomainException {
    public InvalidPlanException(String message) {
        super(message);
    }
}
```

**使用示例**:
```java
// 在领域层抛出
public class PlanAggregate {
    public void validate() {
        if (slices.isEmpty()) {
            throw new InvalidPlanException("Plan must have at least one slice");
        }
    }
}
```

</details>

### 应用异常 (Application Exception)

<details>
<summary>查看定义和使用</summary>

**位置**: `patra-{service}-app/src/main/java/.../exception/`

**目的**: 表示用例编排失败

**基类**:
```java
package com.patra.ingest.app.exception;

public abstract class ApplicationException extends RuntimeException {
    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**具体异常**:
```java
public class ProvenanceConfigNotFoundException extends ApplicationException {
    public ProvenanceConfigNotFoundException(String provenanceCode) {
        super("Provenance configuration not found for: " + provenanceCode);
    }
}

public class OrchestrationException extends ApplicationException {
    public OrchestrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

</details>

---

## 📋 HTTP 错误映射 (ProblemDetail)

### GlobalExceptionHandler 实现

<details>
<summary>查看完整实现</summary>

**位置**: `patra-{service}-adapter/src/main/java/.../rest/GlobalExceptionHandler.java`

**目的**: 将异常映射到 RFC 7807 ProblemDetail 响应

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
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. 领域异常 → 404 Not Found
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

    // 2. 应用异常 → 422 Unprocessable Entity
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

    // 3. 验证错误 → 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://patra.com/errors/validation-error"));

        // 添加字段错误
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    // 4. 通用异常 → 500 Internal Server Error
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

</details>

### ProblemDetail 响应示例

<details>
<summary>查看响应格式</summary>

**请求**:
```bash
GET /api/v1/provenances/UNKNOWN
```

**响应** (404 Not Found):
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

**验证错误响应** (400 Bad Request):
```json
{
  "type": "https://patra.com/errors/validation-error",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "fieldErrors": {
    "name": "must not be blank",
    "email": "must be a well-formed email address"
  },
  "timestamp": "2024-11-01T12:00:00Z"
}
```

</details>

---

## 📊 速查表

### 事务回滚行为

| 异常类型 | 默认回滚 | 配置 |
|---------|---------|------|
| **RuntimeException** | ✅ 是 | 默认 |
| **Checked Exception** | ❌ 否 | `rollbackFor = Exception.class` |
| **Error** | ✅ 是 | 默认 |
| **特定异常不回滚** | - | `noRollbackFor = OptimisticLockException.class` |

### 事务传播行为对比

| 传播行为 | 行为 | 使用场景 |
|---------|------|---------|
| **REQUIRED** | 加入或新建 | 大多数编排者 (默认) |
| **REQUIRES_NEW** | 总是新建 | 审计日志、独立操作 |
| **NESTED** | 嵌套事务 (保存点) | 部分回滚场景 |
| **NOT_SUPPORTED** | 无事务执行 | 只读查询、报表 |
| **MANDATORY** | 必须在事务内 | 内部 Coordinator |
| **NEVER** | 不允许事务 | 特殊场景 |

### 异常到 HTTP 状态码映射

| 异常类型 | HTTP 状态码 | 使用场景 |
|---------|------------|---------|
| `DomainException` (NotFound) | 404 | 资源不存在 |
| `DomainException` (Invalid) | 422 | 业务规则违反 |
| `ApplicationException` | 422 | 编排失败 |
| `MethodArgumentNotValidException` | 400 | 参数验证失败 |
| `OptimisticLockException` | 409 | 并发冲突 |
| `Exception` | 500 | 未知错误 |

---

## 🔧 常见问题与解决

### 问题 1: 事务过长导致性能问题

**症状**: 数据库连接池耗尽,响应时间长

**原因**: 在事务内调用外部 API 或复杂计算

<details>
<summary>查看解决方案</summary>

```java
// ❌ 错误: 长事务
@Transactional
public void processHarvest(HarvestCommand command) {
    BatchPlan plan = createPlan(command);
    List<Publication> results = fetchFromApi(command);  // 慢!
    savePublication(results);
}

// ✅ 正确: 拆分短事务
@Transactional
public BatchPlan createPlan(CreatePlanCommand command) {
    return planRepository.save(assemblePlan(command));
}

public List<Publication> fetchFromApi(HarvestCommand command) {
    return apiClient.search(command.query());  // 无事务
}

@Transactional
public void savePublication(List<Publication> results) {
    literatureRepository.saveAll(results);
}
```

**原则**:
- 每个事务 < 1 秒
- 外部 API 调用在事务外
- 复杂计算在事务外

</details>

### 问题 2: N+1 查询导致性能下降

**症状**: 事务内大量 SQL 查询

**原因**: 循环中逐个查询

<details>
<summary>查看优化方案</summary>

```java
// ❌ 错误: N+1 查询
@Transactional
public void updatePlans(List<BatchPlanId> planIds) {
    for (BatchPlanId id : planIds) {
        BatchPlan plan = planRepository.findById(id);  // N 次查询
        plan.updateStatus(PlanStatus.COMPLETED);
        planRepository.save(plan);
    }
}

// ✅ 正确: 批量查询
@Transactional
public void updatePlans(List<BatchPlanId> planIds) {
    List<BatchPlan> plans = planRepository.findAllById(planIds);  // 1 次查询
    plans.forEach(plan -> plan.updateStatus(PlanStatus.COMPLETED));
    planRepository.saveAll(plans);  // 批量更新
}
```

**优化技巧**:
1. 使用 `findAllById()` 批量查询
2. 使用 `saveAll()` 批量更新
3. 考虑使用 JOIN FETCH 预加载关联

</details>

### 问题 3: 乐观锁冲突处理

**症状**: 并发更新时抛出 `OptimisticLockException`

**原因**: 多个事务同时修改同一记录

<details>
<summary>查看重试策略</summary>

```java
@Service
@RequiredArgsConstructor
public class PlanUpdateOrchestrator {

    private final PlanRepository planRepository;

    @Transactional
    public void updatePlanWithRetry(UpdatePlanCommand command) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                BatchPlan plan = planRepository.findById(command.planId());
                plan.updateStatus(command.status());
                planRepository.save(plan);  // 可能抛出 OptimisticLockException
                return;  // 成功

            } catch (OptimisticLockException e) {
                attempt++;
                log.warn("Optimistic lock conflict, retry {}/{}", attempt, maxRetries);

                if (attempt >= maxRetries) {
                    throw new ApplicationException(
                        "Failed to update plan after " + maxRetries + " retries", e
                    );
                }
                // 短暂等待后重试
                try {
                    Thread.sleep(100 * attempt);  // 递增等待
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ApplicationException("Retry interrupted", ie);
                }
            }
        }
    }
}
```

**策略**:
1. 最多重试 3 次
2. 递增等待时间 (100ms, 200ms, 300ms)
3. 超过重试次数抛出异常

</details>

---

## 🌐 分布式事务

### 方案对比

| 方案 | 一致性 | 性能 | 复杂度 | 推荐场景 |
|------|--------|------|--------|---------|
| **Outbox 模式** | 最终一致 | ⭐⭐⭐⭐⭐ | ⭐⭐ | 大多数场景 |
| **Seata (2PC)** | 强一致 | ⭐⭐ | ⭐⭐⭐⭐ | 金融交易等 |
| **Saga 模式** | 最终一致 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 长事务 |

### 推荐方案: Outbox 模式

<details>
<summary>查看实现</summary>

**优势**:
- 无分布式事务开销
- 最终一致性
- 容错性强
- 性能优秀

**实现**:
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

    private final PlanRepository planRepository;
    private final OutboxMessageRepository outboxRepository;

    @Transactional
    public void createPlan(CreatePlanCommand command) {
        // 1. 保存业务数据
        BatchPlan plan = assemblePlan(command);
        planRepository.save(plan);

        // 2. 保存 Outbox 消息 (同一事务)
        OutboxMessage message = OutboxMessage.builder()
            .aggregateType("BatchPlan")
            .aggregateId(plan.getId().getValue())
            .channel("ingest.plan")
            .opType("PLAN_CREATED")
            .payloadJson(serializePlan(plan))
            .build();
        outboxRepository.save(message);

        // ✅ 业务数据和 Outbox 消息原子提交
    }
}

// 独立进程轮询 Outbox 表并发布到消息队列
@Service
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxMessageRepository outboxRepository;
    private final MessagePublisher messagePublisher;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishOutboxMessages() {
        List<OutboxMessage> pending = outboxRepository.findPending();
        for (OutboxMessage message : pending) {
            try {
                messagePublisher.publish(message);  // RocketMQ
                outboxRepository.markPublished(message.getId());
            } catch (Exception e) {
                log.error("Failed to publish message: {}", message.getId(), e);
                outboxRepository.incrementRetry(message.getId());
            }
        }
    }
}
```

**参考**: [outbox-pattern.md](outbox-pattern.md) 获取完整实现细节

</details>

---

## ✅ 最佳实践检查清单

### 事务管理
- [ ] `@Transactional` 仅在 Orchestrator 层使用
- [ ] 保持事务简短 (< 1 秒)
- [ ] 绝不在事务内调用外部 API
- [ ] 使用适当的传播行为
- [ ] 只读查询使用 `readOnly=true`
- [ ] 避免 N+1 查询,使用批量操作

### 错误处理
- [ ] 领域异常表示业务规则违反
- [ ] 应用异常表示编排失败
- [ ] 在 GlobalExceptionHandler 映射到 ProblemDetail
- [ ] 记录错误日志并包含上下文
- [ ] 不泄露敏感信息到客户端

### 分布式事务
- [ ] 优先使用 Outbox 模式 (最终一致性)
- [ ] 仅在强一致性要求时使用 Seata
- [ ] 考虑幂等性和去重
- [ ] 实现重试机制

---

## 📚 相关文档

### 核心模式
- [outbox-pattern.md](outbox-pattern.md) - 可靠事件发布
- [event-driven-architecture.md](event-driven-architecture.md) - 领域事件
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - 应用层编排

### 实现指南
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - 批量操作和性能优化
- [observability-guide.md](observability-guide.md) - 错误日志和监控
- [testing-guide.md](testing-guide.md) - 事务测试策略

### 代码参考
- **Outbox 实现**: `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/`
- **异常处理**: `patra-registry-adapter/src/main/java/com/patra/registry/adapter/rest/GlobalExceptionHandler.java`
- **事务编排**: `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java`
