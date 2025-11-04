# Patra 核心业务概念

## 概述

本文档描述基于实际数据库架构和 Java 实现的 Patra 核心领域实体。所有实体来自 `patra-ingest-domain` 和 `patra-registry-domain`。

**Entity Hierarchy**:
```
ScheduleInstanceAggregate (Trigger root)
  ↓ 1:N
PlanAggregate (Orchestration blueprint)
  ↓ 1:N
PlanSliceAggregate (Parallelism unit)
  ↓ 1:1
TaskAggregate (Executable unit)
  ↓ 1:N
TaskRun (Execution attempt)
  ↓ 1:N
TaskRunBatch (Pagination batch)
```

---

## 1. Provenance (注册表领域)

### 定义
采集文献数据的数据源(PubMed、EPMC、Crossref 等)。存储在 `patra-registry` 中。

### 实体(值对象)

**表**: `reg_provenance`

**Java** (`patra-registry-domain`):
```java
public record Provenance(
    Long id,
    String code,                // 'PUBMED', 'EPMC', 'CROSSREF'
    String name,                // 显示名称
    String baseUrlDefault,      // 默认基础 URL
    String timezoneDefault,     // 默认时区(例如 'UTC', 'America/New_York')
    String docsUrl,             // 文档 URL
    boolean active,             // 操作标志
    String lifecycleStatusCode  // 'ACTIVE', 'DEPRECATED', 'RETIRED'
)
```

**关键字段**:
| 字段 | 类型 | 描述 | 示例 |
|-------|------|-------------|---------|
| `code` | String | 唯一标识符 | `'PUBMED'` |
| `name` | String | 显示名称 | `'PubMed'` |
| `baseUrlDefault` | String | 默认 API 基础 URL | `'https://eutils.ncbi.nlm.nih.gov'` |
| `timezoneDefault` | String | 默认时区 | `'UTC'` |
| `active` | boolean | 操作状态 | `true` |
| `lifecycleStatusCode` | String | 生命周期状态 | `'ACTIVE'` |

**注意**: Provenance 主表中没有 `description`、`createdAt` 或 `updatedAt` 字段。审计字段存在于 `reg_prov_snapshot` 表中。

---

## 2. ScheduleInstanceAggregate (摄取领域)

### 定义
启动 Plan 创建的外部触发事件(调度器、手动触发、webhook)。

### 聚合根

**表**: `ing_schedule_instance`

**Java** (`patra-ingest-domain`):
```java
public class ScheduleInstanceAggregate extends AggregateRoot<Long> {
    private String schedulerCode;      // 'QUARTZ', 'MANUAL', 'WEBHOOK'
    private String triggerTypeCode;    // 'CRON', 'ONCE', 'MANUAL'
    private Instant triggeredAt;
    private String provenanceCode;     // 普通字符串(非值对象)
    private String operationCode;      // 'HARVEST', 'UPDATE', 'BACKFILL'
    private String statusCode;         // 'TRIGGERED', 'COMPLETED', 'FAILED'
}
```

**状态**:
- **TRIGGERED**: 调度触发
- **COMPLETED**: 所有关联的 Plan 成功
- **FAILED**: 任何 Plan 失败

**目的**: Plan 创建的根实体,跟踪触发源和时机。

---

## 3. PlanAggregate (摄取领域)

### 定义
编排蓝图,包含表达式原型、配置快照、窗口规格和切片策略。

### 聚合根

**表**: `ing_plan`

**Java** (`patra-ingest-domain`):
```java
public class PlanAggregate extends AggregateRoot<Long> {
    // 标识符
    private Long scheduleInstanceId;       // FK 到 ScheduleInstance
    private String planKey;                // 幂等性: "PUBMED:HARVEST:1704067200000-1706745599999"

    // Provenance 与操作
    private String provenanceCode;         // 普通字符串
    private String operationCode;          // 'HARVEST', 'UPDATE', 'BACKFILL'

    // 表达式
    private String exprProtoHash;          // SHA256(expr_proto_snapshot)
    private JsonNode exprProtoSnapshot;    // 表达式原型(无边界)

    // 配置
    private JsonNode provenanceConfigSnapshot;  // 来自注册表的配置快照

    // 窗口与切片
    private JsonNode windowSpec;           // 序列化的 WindowSpec
    private String sliceStrategyCode;      // 'TIME', 'DATE', 'SINGLE'

    // 状态
    private String statusCode;             // 'DRAFT', 'SLICING', 'READY', 'ARCHIVED'
    private Instant createdAt;
}
```

**关键字段**:
| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `planKey` | String | 幂等性键 |
| `exprProtoHash` | String | 表达式原型的 SHA256 |
| `exprProtoSnapshot` | JsonNode | 无边界的表达式 |
| `provenanceConfigSnapshot` | JsonNode | 不可变配置快照 |
| `windowSpec` | JsonNode | WindowSpec (Time/IdRange/Single/等) |
| `sliceStrategyCode` | String | 切片策略 |
| `statusCode` | String | DRAFT/SLICING/READY/ARCHIVED |

**Plan Key 格式**:
```
{provenanceCode}:{operationCode}:{windowFromMillis}-{windowToMillis}
示例: "PUBMED:HARVEST:1704067200000-1706745599999"
```

---

## 4. PlanSliceAggregate (摄取领域)

### 定义
并行化单元,表示 Plan 窗口的时间/ID 切片。每个 Slice 恰好对应一个 Task。

### 聚合根

**表**: `ing_plan_slice`

**Java** (`patra-ingest-domain`):
```java
public class PlanSliceAggregate extends AggregateRoot<Long> {
    // 标识
    private Long planId;                   // FK 到 Plan
    private Integer sliceNo;               // 序列号(1..N)

    // 签名与表达式
    private String sliceSignatureHash;     // SHA256(规范化的 windowSpec)
    private String exprHash;               // SHA256(expr_snapshot)
    private String exprSnapshotJson;       // 本地化表达式(TEXT,含边界)

    // 窗口
    private JsonNode windowSpecJson;       // 缩小的窗口(例如 2024-01-01 至 2024-01-07)

    // 状态
    private String statusCode;             // 'PENDING', 'ASSIGNED', 'FINISHED'
    private Instant createdAt;
}
```

**关键字段**:
| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `sliceNo` | Integer | Plan 内的序列号 |
| `sliceSignatureHash` | String | 规范化窗口的 SHA256(幂等性) |
| `exprHash` | String | 本地化表达式的 SHA256 |
| `exprSnapshotJson` | String (TEXT) | 注入边界的表达式 |
| `windowSpecJson` | JsonNode | Slice 窗口规格 |
| `statusCode` | String | PENDING/ASSIGNED/FINISHED |

**Slice 签名哈希**:
```
输入: WindowSpec.Time(from=2024-01-01T00:00:00Z, to=2024-01-07T23:59:59Z)
规范化: "Time{from:1704067200000,to:1704671999000}"
哈希: SHA256(canonical) → "a7f3e2d1c4b8..."
```

**状态**:
- **PENDING**: Slice 已创建,Task 尚未创建
- **ASSIGNED**: Task 已创建并分发
- **FINISHED**: Task 执行完成

---

## 5. TaskAggregate (摄取领域)

### 定义
可执行单元,表示一个 Plan Slice。通过基于租约的分布式执行进行管理。

### 聚合根

**表**: `ing_task`

**Java** (`patra-ingest-domain`):
```java
public class TaskAggregate extends AggregateRoot<Long> {
    // 标识
    private Long sliceId;                  // FK 到 PlanSlice (1:1, UNIQUE)
    private String idempotentKey;          // Base64Url(SHA256(provenance|operation|sliceSignatureHash))

    // Provenance 与操作
    private String provenanceCode;         // 普通字符串
    private String operationCode;          // 'HARVEST', 'UPDATE', 'BACKFILL'

    // 表达式
    private String exprHash;               // 与 slice.exprHash 相同

    // 参数
    private String paramsJson;             // JSON: {"sliceNo": 5}

    // 调度
    private Integer priority;              // 执行优先级
    private Instant scheduledAt;           // 执行时间

    // 租约(分布式锁)
    private LeaseInfo leaseInfo;           // 所有者,过期时间

    // 状态
    private String statusCode;             // 'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'
    private Integer retryCount;            // 当前重试次数

    // 时间线
    private ExecutionTimeline executionTimeline;  // 开始/结束时间

    private Instant createdAt;
}
```

**关键字段**:
| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `sliceId` | Long | 1:1 FK 到 PlanSlice (UNIQUE) |
| `idempotentKey` | String | Base64Url 编码的 SHA256 哈希 |
| `paramsJson` | String | Task 元数据(非 API 参数) |
| `leaseInfo` | LeaseInfo | 租约所有者+过期时间 |
| `statusCode` | String | QUEUED/RUNNING/SUCCEEDED/FAILED |
| `retryCount` | Integer | 当前重试尝试 |
| `executionTimeline` | ExecutionTimeline | 时间信息 |

**幂等键格式**:
```
复合: "{provenanceCode}|{operationCode}|{sliceSignatureHash}"
哈希: SHA256(composite)
编码: Base64Url(hash)
示例: "x3jK8pL2mN9qR4tV6wY8zA"
```

**Task 参数** (paramsJson):
```json
{
  "sliceNo": 5
}
```
**注意**: 仅包含元数据。API 参数来自表达式渲染。

**状态**:
- **QUEUED**: 已创建,等待 worker
- **RUNNING**: Worker 已获取租约
- **SUCCEEDED**: 成功完成
- **FAILED**: 失败,重试次数已用尽

---

## 6. LeaseInfo (值对象)

### 定义
分布式任务锁定信息。

**Java** (`patra-ingest-domain`):
```java
public record LeaseInfo(
    String leaseOwner,        // Worker 实例 ID
    Instant leasedUntil       // 租约过期时间
) {
    public boolean isExpired() {
        return leasedUntil != null && Instant.now().isAfter(leasedUntil);
    }

    public boolean isOwnedBy(String workerId) {
        return leaseOwner != null && leaseOwner.equals(workerId);
    }
}
```

**数据库表示**:
```sql
-- ing_task 表
lease_owner VARCHAR(128),
leased_until TIMESTAMP(3)
```

**目的**: 防止多个 worker 同时执行同一个任务。

---

## 7. ExecutionTimeline (值对象)

### 定义
Task 执行时间信息。

**Java** (`patra-ingest-domain`):
```java
public record ExecutionTimeline(
    Instant startedAt,
    Instant completedAt,
    Instant lastHeartbeatAt
) {
    public Duration duration() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt);
    }

    public boolean isRunning() {
        return startedAt != null && completedAt == null;
    }
}
```

**目的**: 跟踪任务执行生命周期。

---

## 8. TaskRun (实体)

### 定义
表示任务的一次执行尝试(支持重试)。

**表**: `ing_task_run`

**Java** (`patra-ingest-domain`):
```java
public class TaskRun extends Entity<Long> {
    private Long taskId;               // FK 到 Task
    private Integer attemptNo;         // 1, 2, 3, ...
    private String statusCode;         // 'PENDING', 'RUNNING', 'PARTIAL', 'SUCCEEDED', 'FAILED'
    private Instant startedAt;
    private Instant completedAt;
    private JsonNode checkpoint;       // 用于恢复的游标状态
    private JsonNode stats;            // {"recordsFetched": 1500, "recordsStored": 1498}
    private String errorMessage;       // 失败时的错误详情
}
```

**关键字段**:
| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `attemptNo` | Integer | 重试尝试编号 |
| `statusCode` | String | PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED |
| `checkpoint` | JsonNode | 分页恢复点 |
| `stats` | JsonNode | 执行统计 |
| `errorMessage` | String | 失败原因 |

**状态**:
- **PENDING**: 已创建,未开始
- **RUNNING**: 正在执行
- **PARTIAL**: 分页中暂停(已保存检查点)
- **SUCCEEDED**: 成功完成
- **FAILED**: 失败(触发任务重试)

**检查点示例**:
```json
{
  "cursor": "abc123def456",
  "lastProcessedId": 9876543,
  "batchNo": 25
}
```

---

## 9. TaskRunBatch (实体)

### 定义
表示任务运行中的一个分页批次。

**表**: `ing_task_run_batch`

**Java** (`patra-ingest-domain`):
```java
public class TaskRunBatch extends Entity<Long> {
    private Long runId;                // FK 到 TaskRun
    private Integer batchNo;           // 1, 2, 3, ...
    private String beforeToken;        // 获取前的游标/偏移量
    private String afterToken;         // 获取后的游标/偏移量
    private Integer recordsFetched;    // API 返回的记录数
    private Integer recordsStored;     // 实际存储的记录数
    private String idempotentKey;      // 去重键
    private Instant createdAt;
}
```

**关键字段**:
| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `batchNo` | Integer | 运行内的序列号 |
| `beforeToken` | String | 获取前的分页游标 |
| `afterToken` | String | 获取后的分页游标 |
| `recordsFetched` | Integer | API 响应数量 |
| `recordsStored` | Integer | 成功持久化数量 |

**分页令牌** (PubMed 基于偏移量的示例):
```
Batch 1: beforeToken="0",    afterToken="1000"
Batch 2: beforeToken="1000", afterToken="2000"
Batch 3: beforeToken="2000", afterToken="3000"
```

**分页令牌** (基于游标的示例):
```
Batch 1: beforeToken=null,          afterToken="cursor_abc123"
Batch 2: beforeToken="cursor_abc123", afterToken="cursor_def456"
```

---

## 10. WindowSpec (密封接口)

### 定义
具有多种策略的窗口边界规格。

**Java** (`patra-ingest-domain`):
```java
public sealed interface WindowSpec permits
    WindowSpec.Time,
    WindowSpec.IdRange,
    WindowSpec.CursorLandmark,
    WindowSpec.VolumeBudget,
    WindowSpec.Single
{
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long from, Long to) implements WindowSpec {}
    record CursorLandmark(String from, String to) implements WindowSpec {}
    record VolumeBudget(Long limit, String unit) implements WindowSpec {}
    record Single() implements WindowSpec {}
}
```

**变体**:
| 变体 | 使用场景 | 示例 |
|---------|----------|---------|
| **Time** | 基于时间的窗口 | `Time(2024-01-01T00:00:00Z, 2024-01-31T23:59:59Z)` |
| **IdRange** | 基于 ID 的分页 | `IdRange(1L, 1000000L)` |
| **CursorLandmark** | 基于游标的 API | `CursorLandmark("start", "end")` |
| **VolumeBudget** | 容量限制 | `VolumeBudget(10000L, "RECORDS")` |
| **Single** | 无切片 | `Single()` |

---

## 实体关系

### 数据库外键

```sql
ing_schedule_instance
  ↓ (1:N 通过 schedule_instance_id)
ing_plan
  ↓ (1:N 通过 plan_id)
ing_plan_slice
  ↓ (1:1 通过 slice_id, UNIQUE)
ing_task
  ↓ (1:N 通过 task_id)
ing_task_run
  ↓ (1:N 通过 run_id)
ing_task_run_batch
```

### 关键约束

**1:1 Slice-Task 关系**:
```sql
ALTER TABLE ing_task
ADD UNIQUE KEY uk_task_slice(slice_id);
```

**Plan 幂等性**:
```sql
ALTER TABLE ing_plan
ADD UNIQUE KEY uk_plan_key(plan_key);
```

**Task 幂等性**:
```sql
ALTER TABLE ing_task
ADD UNIQUE KEY uk_task_idempotent(idempotent_key);
```

---

## 聚合 vs 实体 vs 值对象

### 聚合根(具有标识+生命周期)
- ScheduleInstanceAggregate
- PlanAggregate
- PlanSliceAggregate
- TaskAggregate

### 实体(具有标识,由聚合拥有)
- TaskRun (由 TaskAggregate 拥有)
- TaskRunBatch (由 TaskRun 拥有)

### 值对象(不可变,无标识)
- Provenance (在注册表上下文中)
- WindowSpec
- LeaseInfo
- ExecutionTimeline
- ProvenanceConfigSnapshot

---

## 命名约定

**实际 vs 文档**:
| 技能文档 | 实际代码 |
|---------------------|-------------|
| `BatchPlan` | `PlanAggregate` |
| `BatchTask` | `TaskAggregate` |
| `Slice` | `PlanSliceAggregate` |
| `businessKey` | `idempotentKey` |
| `Map<String, Object> params` | `String paramsJson` |
| `ProvenanceCode` (值对象) | `String provenanceCode` |

---

## 总结

**核心实体**:
1. **ScheduleInstanceAggregate**: 触发根节点
2. **PlanAggregate**: 编排蓝图
3. **PlanSliceAggregate**: 并行化单元(与 Task 1:1)
4. **TaskAggregate**: 带租约的可执行单元
5. **TaskRun**: 执行尝试
6. **TaskRunBatch**: 分页批次

**关键概念**:
- **1:1:1 层次结构**: Plan → PlanSlice → Task (由数据库强制)
- **幂等性**: Plan 键、Task 幂等键、Slice 签名哈希
- **基于租约的执行**: 通过 LeaseInfo 进行分布式任务锁定
- **快照隔离**: Plan 级别的配置+表达式快照
- **分页**: 在 TaskRunBatch 级别处理(非 Task 参数)

**数据库表**:
- `ing_schedule_instance`, `ing_plan`, `ing_plan_slice`, `ing_task`, `ing_task_run`, `ing_task_run_batch`
- `reg_provenance` (在 patra-registry 中)
