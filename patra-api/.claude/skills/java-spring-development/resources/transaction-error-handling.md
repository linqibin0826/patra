# 事务与错误处理指南

## 快速开始

### 事务管理模板

```java
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
```

### 领域异常

```java
// 定义领域异常
public class InvalidPlanException extends DomainException {
    public InvalidPlanException(String message) {
        super(message);
    }
}

// 在 GlobalExceptionHandler 映射到 HTTP 响应
@ExceptionHandler(InvalidPlanException.class)
public ProblemDetail handleInvalidPlan(InvalidPlanException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ex.getMessage()
    );
}
```

## 决策矩阵

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
  └─ 不需要事务 → NOT_SUPPORTED
      └─ 用例: 只读查询、报表
```

## 核心概念

### 为什么事务边界在 Orchestrator?

**❌ 错误：在 Repository 层**
```java
@Repository
public class PlanRepositoryImpl implements PlanRepository {
    @Transactional  // ❌ 太细粒度，无法协调多个操作
    public void save(PlanAggregate plan) { }
}
```

**❌ 错误：在 Domain 层**
```java
public class PlanAggregate {
    @Transactional  // ❌ 领域是纯 Java，不能用 Spring
    public void addSlice(PlanSlice slice) { }
}
```

**✅ 正确：在 Orchestrator 层**
```java
@Service
public class PlanIngestionOrchestrator {
    @Transactional  // ✅ 协调多个操作，自然的事务边界
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        planRepository.save(plan);
        sliceRepository.saveAll(slices);
        outboxRepository.save(message);
    }
}
```

**原因**：
1. Orchestrator 协调用例 → 自然的事务边界
2. Domain 层是纯 Java → 不能使用 Spring 注解
3. Infrastructure 层方法太细粒度 → 无法协调多个操作

## 事务管理模式

### 模式 1: 标准事务 (REQUIRED)

**使用场景**: 大多数编排者，需要加入现有事务或新建事务

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {
    private final PlanRepository planRepository;
    private final SliceRepository sliceRepository;

    @Transactional  // 默认 Propagation.REQUIRED
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        PlanAggregate plan = assemblePlan(command);
        planRepository.save(plan);

        List<PlanSlice> slices = generateSlices(plan);
        sliceRepository.saveAll(slices);

        return new PlanCreationResult(plan.getId(), slices.size());
    }
}
```

### 模式 2: 独立事务 (REQUIRES_NEW)

**使用场景**: 审计日志、独立操作，即使主事务回滚也要提交

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
        // ✅ 即使调用方事务回滚，日志也会保存
    }
}
```

### 模式 3: 只读事务 (readOnly=true)

**使用场景**: 查询操作，无写入需求

```java
@Transactional(readOnly = true)
public List<PlanAggregate> findActivePlans() {
    return planRepository.findByStatus(PlanStatus.RUNNING);
}
```

**优化效果**：
1. 数据库层面优化（跳过脏检查、刷新操作）
2. 防止意外写入
3. 某些数据库可能路由到只读副本

## 外部 API 调用规则

### 规则: 绝不在 @Transactional 内调用外部 API

**原因**：
1. 外部 API 调用慢 → 事务持续时间过长
2. 网络故障 → 事务超时
3. 数据库连接池耗尽
4. 无法回滚外部 API 调用

**❌ 错误：外部 API 在事务内**
```java
@Transactional
public void harvestData(HarvestCommand command) {
    planRepository.save(plan);  // 事务开始

    // ❌ 危险！如果 API 耗时 10 秒，数据库事务持续 10+ 秒
    SearchResult results = pubmedSearchPort.search(query);

    publicationStoragePort.saveAll(results);
}
```

**✅ 正确：外部 API 在事务外**
```java
@Service
@RequiredArgsConstructor
public class HarvestOrchestrator {

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
        publicationStoragePort.save(results);
    }
}
```

## 异常设计

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

**位置**: `patra-{service}-domain/src/main/java/.../exception/`

**基类**：
```java
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}
```

**具体异常**：
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

### 应用异常 (Application Exception)

**位置**: `patra-{service}-app/src/main/java/.../exception/`

**基类**：
```java
public abstract class ApplicationException extends RuntimeException {
    protected ApplicationException(String message) {
        super(message);
    }
}
```

## HTTP 错误映射 (ProblemDetail)

### GlobalExceptionHandler 实现

**位置**: `patra-{service}-adapter/src/main/java/.../rest/GlobalExceptionHandler.java`

```java
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
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    // 3. 验证错误 → 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
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
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }
}
```

### ProblemDetail 响应示例

**请求**：
```bash
GET /api/v1/provenances/UNKNOWN
```

**响应** (404 Not Found)：
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

## 速查表

### 事务回滚行为

| 异常类型 | 默认回滚 | 配置 |
|---------|---------|------|
| **RuntimeException** | ✅ 是 | 默认 |
| **Checked Exception** | ❌ 否 | `rollbackFor = Exception.class` |
| **Error** | ✅ 是 | 默认 |

### 事务传播行为对比

| 传播行为 | 行为 | 使用场景 |
|---------|------|---------|
| **REQUIRED** | 加入或新建 | 大多数编排者 (默认) |
| **REQUIRES_NEW** | 总是新建 | 审计日志、独立操作 |
| **NOT_SUPPORTED** | 无事务执行 | 只读查询、报表 |
| **MANDATORY** | 必须在事务内 | 内部 Coordinator |

### 异常到 HTTP 状态码映射

| 异常类型 | HTTP 状态码 | 使用场景 |
|---------|------------|---------|
| `DomainException` (NotFound) | 404 | 资源不存在 |
| `DomainException` (Invalid) | 422 | 业务规则违反 |
| `ApplicationException` | 422 | 编排失败 |
| `MethodArgumentNotValidException` | 400 | 参数验证失败 |
| `Exception` | 500 | 未知错误 |

## 常见问题与解决

### 问题 1: 事务过长导致性能问题

**症状**: 数据库连接池耗尽，响应时间长

**原因**: 在事务内调用外部 API 或复杂计算

```java
// ❌ 错误：长事务
@Transactional
public void processHarvest(HarvestCommand command) {
    BatchPlan plan = createPlan(command);
    List<Publication> results = fetchFromApi(command);  // 慢！
    savePublication(results);
}

// ✅ 正确：拆分短事务
@Transactional
public BatchPlan createPlan(CreatePlanCommand command) {
    return planRepository.save(assemblePlan(command));
}

public List<Publication> fetchFromApi(HarvestCommand command) {
    return apiClient.search(command.query());  // 无事务
}

@Transactional
public void savePublication(List<Publication> results) {
    publicationRepository.saveAll(results);
}
```

**原则**：
- 每个事务 < 1 秒
- 外部 API 调用在事务外
- 复杂计算在事务外

### 问题 2: 乐观锁冲突处理

**症状**: 并发更新时抛出 `OptimisticLockException`

**解决方案：重试策略**

```java
@Transactional
public void updatePlanWithRetry(UpdatePlanCommand command) {
    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
        try {
            BatchPlan plan = planRepository.findById(command.planId());
            plan.updateStatus(command.status());
            planRepository.save(plan);
            return;  // 成功

        } catch (OptimisticLockException e) {
            attempt++;
            if (attempt >= maxRetries) {
                throw new ApplicationException("Failed after " + maxRetries + " retries", e);
            }
            // 短暂等待后重试
            Thread.sleep(100 * attempt);  // 递增等待
        }
    }
}
```

## 分布式事务

### 方案对比

| 方案 | 一致性 | 性能 | 复杂度 | 推荐场景 |
|------|--------|------|--------|---------|
| **Outbox 模式** | 最终一致 | ⭐⭐⭐⭐⭐ | ⭐⭐ | 大多数场景 |
| **Seata (2PC)** | 强一致 | ⭐⭐ | ⭐⭐⭐⭐ | 金融交易等 |
| **Saga 模式** | 最终一致 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 长事务 |

### 推荐方案: Outbox 模式

**优势**：
- 无分布式事务开销
- 最终一致性
- 容错性强
- 性能优秀

**实现**：
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

        // 2. 保存 Outbox 消息（同一事务）
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
```

**参考**: [outbox-pattern.md](outbox-pattern.md) 获取完整实现细节

## 最佳实践检查清单

### 事务管理
- [ ] `@Transactional` 仅在 Orchestrator 层使用
- [ ] 保持事务简短 (< 1 秒)
- [ ] 绝不在事务内调用外部 API
- [ ] 使用适当的传播行为
- [ ] 只读查询使用 `readOnly=true`

### 错误处理
- [ ] 领域异常表示业务规则违反
- [ ] 应用异常表示编排失败
- [ ] 在 GlobalExceptionHandler 映射到 ProblemDetail
- [ ] 记录错误日志并包含上下文
- [ ] 不泄露敏感信息到客户端

### 分布式事务
- [ ] 优先使用 Outbox 模式（最终一致性）
- [ ] 仅在强一致性要求时使用 Seata
- [ ] 考虑幂等性和去重
- [ ] 实现重试机制
