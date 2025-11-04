# patra-ingest — 数据摄入编排服务

> **数据采集编排引擎**,负责计划编排、任务生成、窗口解析、游标跟踪和 Outbox 中继。

---

## 📌 核心职责

`patra-ingest` 服务是 Patra 医学文献数据平台的**数据摄入编排核心**,负责:

1. **计划编排(Plan Orchestration)**: 根据调度触发器创建执行计划,分解为原子任务
2. **窗口解析(Window Resolution)**: 确定数据采集的时间/容量边界,支持增量采集
3. **任务生成(Task Generation)**: 将计划切片为可并行执行的任务,保证幂等性
4. **游标跟踪(Cursor Tracking)**: 维护增量采集的水位线(Watermark)
5. **Outbox 中继(Outbox Relay)**: 通过 Outbox 模式可靠地将任务事件发布到 MQ
6. **执行协调(Execution Coordination)**: 跟踪任务状态、租约管理、批次规划

**核心原则**: 确保**至少一次交付(At-least-once Delivery)**和**幂等任务执行(Idempotent Task Execution)**。

---

## 🏗️ 架构概览

### 六边形架构 + DDD

本服务采用**六边形架构(Hexagonal Architecture)**和**领域驱动设计(DDD)**:

```
┌─────────────────────────────────────────────────────────┐
│  patra-ingest-boot (启动模块)                            │
│  └─ PatraIngestApplication.java                        │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│  patra-ingest-adapter (适配器层 - 驱动适配器)            │
│  ├─ scheduler/  - XXL-Job 定时任务(触发计划摄入)         │
│  └─ rocketmq/   - RocketMQ 消费者(任务执行)             │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│  patra-ingest-app (应用层 - 用例编排)                    │
│  ├─ usecase/plan/        - PlanIngestionOrchestrator   │
│  ├─ usecase/execution/   - TaskExecutionUseCase        │
│  └─ usecase/relay/       - OutboxRelayOrchestrator     │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│  patra-ingest-domain (领域层 - 纯 Java)                 │
│  ├─ model/aggregate/     - Plan, Task, PlanSlice       │
│  ├─ model/vo/            - WindowSpec, ExecutionContext│
│  └─ port/                - 仓储端口、外部服务端口        │
└─────────────────┬───────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────┐
│  patra-ingest-infra (基础设施层 - 被驱动适配器)          │
│  ├─ persistence/         - MyBatis-Plus 仓储实现        │
│  ├─ integration/         - Feign 客户端(Registry/PubMed)│
│  └─ messaging/           - RocketMQ 发布器              │
└─────────────────────────────────────────────────────────┘
```

### 模块说明

| 模块 | 职责 | 核心类 |
|------|------|--------|
| **patra-ingest-boot** | Spring Boot 启动入口 | `PatraIngestApplication` |
| **patra-ingest-adapter** | 驱动适配器(定时任务、MQ 消费者) | `PubmedHarvestJob`, `TaskReadyMessageListener` |
| **patra-ingest-app** | 应用层编排器(事务边界) | `PlanIngestionOrchestrator`, `TaskExecutionUseCase` |
| **patra-ingest-domain** | 领域模型(纯 Java,无框架依赖) | `PlanAggregate`, `TaskAggregate`, `WindowSpec` |
| **patra-ingest-infra** | 基础设施实现(数据库、RPC、MQ) | `PlanRepositoryMpImpl`, `PatraRegistryAdapter` |
| **patra-ingest-api** | 外部 API 契约(错误码、Future APIs) | `IngestErrorCode` |

---

## 🔑 核心领域概念

### 1. Plan (计划聚合根)

**定义**: 数据采集任务的蓝图,包含窗口规范、表达式快照和配置快照。

**状态机**:
```
DRAFT → SLICING → READY → COMPLETED
                     ↓
                 PARTIAL → FAILED
```

**核心属性**:
- `planKey`: 幂等键 = hash(provenance + operation + window + strategy)
- `provenanceCode`: 来源代码(如 `"pubmed"`)
- `windowSpec`: 窗口边界(TIME/DATE/CURSOR/VOLUME/SINGLE)
- `exprProtoSnapshotJson`: 表达式原型快照(JSON)

**文件**: [`patra-ingest-domain/.../PlanAggregate.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java)

### 2. Task (任务聚合根)

**定义**: 数据采集的原子工作单元(如:获取 PubMed 记录 1-1000)。

**状态机**:
```
QUEUED → RUNNING → SUCCEEDED
           ↓
        FAILED → (重试) → QUEUED
```

**核心属性**:
- `idempotentKey`: 业务幂等键
- `paramsJson`: 任务参数(JSON)
- `leaseInfo`: 租约信息(owner, leasedUntil)
- `executionTimeline`: 执行时间线(startedAt, completedAt)

**文件**: [`patra-ingest-domain/.../TaskAggregate.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/TaskAggregate.java)

### 3. WindowSpec (窗口规范 - Sealed Interface)

**定义**: 数据采集的窗口边界,支持多种窗口类型。

```java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}      // 时间窗口
    record IdRange(Long minId, Long maxId) implements WindowSpec {}     // ID 范围窗口
    record CursorLandmark(String cursorValue) implements WindowSpec {}  // 游标窗口
    record VolumeBudget(Integer maxRecords) implements WindowSpec {}    // 容量窗口
    record Single() implements WindowSpec {}                             // 无窗口
}
```

**文件**: [`patra-ingest-domain/.../WindowSpec.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/plan/WindowSpec.java)

---

## 🔄 Plan Ingestion Flow (核心流程)

### 高层序列

```
1. Scheduler 触发(Cron 或手动)
   ↓
2. PlanIngestionOrchestrator.ingestPlan(command)
   ↓
3. Phase 1: 持久化调度实例 + 加载 Provenance 配置快照
   ↓
4. Phase 2: 查询游标水位线 + 解析规划窗口
   ↓
5. Phase 3: 构建 Plan 表达式(未编译快照)
   ↓
6. Phase 4: 预验证(窗口合法性、背压检查、容量检查)
   ↓
7. Phase 5: 装配 Plan/Slice/Task(带幂等性检查)
   ↓
8. Phase 6: 持久化 Plan → Slice → Task(事务内)
   ↓
9. Phase 7: 收集 TaskQueuedEvent → 发布到 Outbox
   ↓
10. Outbox 中继轮询 → 发布到 MQ
   ↓
11. Task 工作者消费 MQ → 执行 → 更新任务状态
```

### 代码入口

- **Plan 摄入**: [`PlanIngestionOrchestrator.java`](patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java)
- **Outbox 中继**: [`OutboxRelayOrchestrator.java`](patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/OutboxRelayOrchestrator.java)
- **任务执行**: [`TaskExecutionUseCaseImpl.java`](patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/TaskExecutionUseCaseImpl.java)

---

## 🔌 表达式编译集成

`patra-ingest` 在执行时编译表达式,并将编译后的参数直接绑定到 Provider 请求模型。

### 编译流程

```java
// 1. ExecutionContextLoader 加载任务上下文时编译表达式
ExprCompilationResult compilationResult = expressionCompilerPort.compile(
    new ExprCompilationRequest(plan.getExprProtoSnapshotJson(), configSnapshot)
);

// 2. 构建 ExecutionContext(包含编译后的参数)
return new ExecutionContext(
    taskId, runId,
    task.getProvenanceCode(),
    task.getOperationCode(),
    configSnapshot,
    compilationResult.query(),       // compiledQuery(通过 std_key=query 桥接)
    compilationResult.params(),      // compiledParams(provider-named)
    compilationResult.normalizedExpression(),
    windowSpec
);

// 3. Provider 适配器使用 compiledParams
PlanMetadata metadata = searchPort.preparePlanMetadata(
    compiledQuery,
    compiledParams,  // 直接使用,不再手动拼接
    configSnapshot
);
```

### 关键规则

- ✅ **始终使用 compiledParams**,不再手动构建查询字符串
- ✅ **MULTI std_keys 默认使用 JOIN 转换**,避免重复参数
- ✅ **生产环境启用 STRICT 模式**(`expr.strict=true`),早期捕获配置错误

---

## 🗄️ 数据库表概览

| 表名 | 说明 | 核心索引 |
|------|------|----------|
| `ingest_plan` | 计划蓝图 | `idx_plan_key` (幂等性) |
| `ingest_task` | 原子任务 | `idx_task_idempotent_key`, `idx_task_status_scheduled` |
| `ingest_plan_slice` | 计划切片 | `idx_slice_plan_id` |
| `ingest_schedule_instance` | 调度实例 | `idx_schedule_key` |
| `ingest_cursor` | 游标水位线 | `idx_cursor_namespace` |
| `ingest_outbox_message` | Outbox 消息 | `idx_outbox_status_seq` (中继轮询) |
| `ingest_outbox_relay_log` | 中继日志 | `idx_relay_batch_id` |
| `ingest_task_run` | 任务运行记录 | `idx_run_task_id` |

---

## 🚀 快速开始

### 1. 启动依赖服务

```bash
# 启动 MySQL + RocketMQ + MinIO
docker-compose -f docker/docker-compose.yml up -d
```

### 2. 运行服务

```bash
cd patra-ingest/patra-ingest-boot
../../mvnw spring-boot:run
```

**默认端口**: 8082

### 3. 触发 Plan 摄入(通过 XXL-Job)

在 XXL-Job 控制台配置定时任务:
- **任务名称**: PubMed Harvest
- **Cron**: `0 0 2 * * ?` (每天凌晨 2 点)
- **任务参数**: `{"provenanceCode":"pubmed","operationCode":"HARVEST"}`

---

## 📊 可观测性

### 日志

- **INFO**: 主要里程碑(如 "Plan ingestion success, planId=123, taskCount=50")
- **DEBUG**: 诊断详情(如 "Window resolved: [2025-01-01, 2025-01-10)")
- **ERROR**: 失败原因(如 "Plan assembly failed: WINDOW_INVALID")

### 指标(Micrometer)

**Outbox 指标**:
- `patra.outbox.publish.total` (Counter) - 发布总数
- `patra.outbox.publish.duration` (Timer) - 发布耗时
- `patra.outbox.publish.batch.size` (DistributionSummary) - 批次大小分布

**访问方式**:
```bash
# 健康检查
curl http://localhost:8082/actuator/health

# 查看指标
curl http://localhost:8082/actuator/metrics/patra.outbox.publish.total
```

---

## 🔗 子模块文档

- [patra-ingest-api](patra-ingest-api/README.md) - API 契约(错误码)
- [patra-ingest-domain](patra-ingest-domain/README.md) - 领域模型(聚合根、值对象、端口接口)
- [patra-ingest-app](patra-ingest-app/README.md) - 应用层编排器(用例编排、事务边界)
- [patra-ingest-infra](patra-ingest-infra/README.md) - 基础设施层(仓储、RPC、MQ)
- [patra-ingest-adapter](patra-ingest-adapter/README.md) - 适配器层(定时任务、MQ 消费者)
- [patra-ingest-boot](patra-ingest-boot/README.md) - 启动模块

---

## 🛠️ 技术栈

- **Java**: 25 (Record, Sealed Interface, Pattern Matching)
- **Spring Boot**: 3.5.7
- **Spring Cloud**: 2025.0.0
- **MyBatis-Plus**: 3.5.x (ORM)
- **MapStruct**: 1.5.x (对象转换)
- **RocketMQ**: 消息队列
- **MinIO**: 对象存储
- **patra-expr-kernel**: 表达式编译内核
- **XXL-Job**: 分布式任务调度

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.patra:patra-ingest:0.1.0-SNAPSHOT`
**作者**: linqibin
