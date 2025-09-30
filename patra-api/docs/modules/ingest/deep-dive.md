# patra-ingest 模块详解

> 内容迁移自历史 README，完整保留采集计划、幂等策略与 Outbox 流程说明。

统一的“计划式采集 + 任务排队”引擎：从调度触发开始，按来源 (provenance) + 操作 (operation) 拉取配置 → 解析采集窗口 → 生成计划 (Plan) → 切片 (PlanSlice) → 任务 (Task) → 写入 Outbox → Relay 发布至 MQ，保障幂等、安全、可回放、可扩展、可观察。

当前实现重点：
1. 窗口策略：HARVEST / BACKFILL / UPDATE
2. 切片策略：TIME、SINGLE，可扩展注册
3. 计划装配：Plan + Slice + Task 原子构建与部分失败处理
4. 幂等体系：表达式/配置/切片规范化哈希 + 任务幂等键 + Outbox 去重字段
5. Outbox & Relay：租约、指数退避、死信标记、分区顺序，强类型通道目录（ChannelKey）
6. 错误码映射：ING-12xx..16xx 覆盖验证、装配、持久化、Outbox、外部依赖

## 1. 模块角色

| 模块 | 说明 |
| ---- | ---- |
| patra-ingest-api | 错误码与对外 DTO（当前主要是错误码枚举） |
| patra-ingest-adapter | XXL-Job 调度入口（Inbound Only）/ Outbox Relay Job |
| patra-ingest-app | **用例层架构**：<br>- `usecase/plan`：计划编排用例（窗口解析、切片、组装、验证、发布）<br>- `usecase/relay`：Outbox 转发用例（租约管理、批量发布、失败分类与退避）<br>- 遵循 DDD + 六边形架构，清晰的用例边界与统一命名规范 |
| patra-ingest-domain | 聚合与领域异常（Plan / PlanSlice / Task / ScheduleInstance 等）以及领域端口（如 `PatraRegistryPort`）<br>消息通道目录（ChannelKey / IngestChannels），统一发送/接收语义 |
| patra-ingest-infra | MyBatis-Plus DO、Mapper、仓储实现、Outbox 持久化 / RPC 出站（`infra.rpc.registry.*`） |
| patra-ingest-boot | Spring Boot 启动与错误码映射装配 |

> **应用层设计原则**：
> - 每个用例自包含：command、dto、核心逻辑、支持组件
> - 统一命名：`*Orchestrator`（编排器）、`*Command`（命令）、`*Impl`（实现）
> - 职责清晰：`plan` 聚焦计划生命周期，`relay` 专注 Outbox 批次执行
> - 遵循六边形架构：用例层不依赖框架，仅依赖领域接口

### 1.1 应用层架构详解

#### Plan 用例（计划编排）

**核心组件**：
- `PlanIngestionOrchestrator`：主编排器，协调整个计划生成流程
- `PlanAssembler`：组装器，负责 Plan/Slice/Task 的蓝图构建
- `SlicePlanner`：切片策略（TIME/SINGLE），支持策略注册扩展
- `PlanningWindowResolver`：窗口解析器，处理 HARVEST/BACKFILL/UPDATE 模式
- `PlanExpressionBuilder`：表达式构建器，生成规范化表达式快照
- `PlannerValidator`：前置验证器，校验窗口合法性与队列压力
- `TaskOutboxPublisher`：Outbox 发布器，将任务事件持久化

**目录结构**：
```
usecase/plan/
├── PlanIngestionUseCase.java          # 用例接口
├── PlanIngestionOrchestrator.java     # 主编排器
├── command/PlanIngestionCommand.java  # 命令对象
├── dto/PlanIngestionResult.java       # 结果 DTO
├── assembler/                         # 组装器
│   ├── PlanAssembler.java
│   ├── PlanAssemblerImpl.java
│   └── PlanAssemblyRequest.java
├── slicer/                            # 切片策略
│   ├── SlicePlanner.java
│   ├── SlicePlannerRegistry.java
│   ├── TimeSlicePlanner.java
│   ├── SingleSlicePlanner.java
│   └── model/
├── window/                            # 窗口解析
│   ├── PlanningWindowResolver.java
│   ├── PlanningWindowResolverImpl.java
│   └── support/
├── expression/                        # 表达式构建
├── validator/                         # 验证器
└── publisher/                         # 发布器
```

#### Relay 用例（Outbox 转发）

**核心组件**：
- `OutboxRelayOrchestrator`：主编排器，协调批量转发流程
- `OutboxRelayExecutor`：执行器，处理单批次消息发布
- `RelayPlanBuilder`：计划构建器，生成转发计划
- `RelayErrorClassifier`：错误分类器，区分 FATAL/TRANSIENT 错误
- `RelayEventPublisher`：事件发布器，发布领域事件

**目录结构**：
```
usecase/relay/
├── OutboxRelayUseCase.java           # 用例接口
├── OutboxRelayOrchestrator.java      # 主编排器
├── command/OutboxRelayCommand.java   # 命令对象
├── dto/RelayReport.java              # 结果 DTO
├── executor/                         # 执行器
├── planner/                          # 计划构建
├── policy/                           # 错误分类
├── publisher/                        # 事件发布
├── config/                           # 配置
└── support/                          # 工具
```

## 2. 核心领域概念

### 命名约定与设计原则

**聚合根命名**：所有聚合根类保留 `Aggregate` 后缀（如 `PlanAggregate`、`TaskAggregate`），这是团队明确的命名约定，理由：
1. **显式识别**：在代码库中快速识别聚合根与普通实体/值对象的区别
2. **一致性**：与 `PlanAssembly`（非聚合但命名特殊）形成一致的命名风格
3. **明确边界**：在六边形架构中清晰标识聚合边界

**包组织原则**（v0.1.0 重构）：
- 领域事件统一在 `domain/event/`（避免重复目录）
- 值对象统一在 `domain/model/vo/`（如 `PlanTriggerNorm`，从 `command/` 重新分类）
- Command/Query 对象属于应用层关注点，不应出现在 domain 层

> 相关重构详情见仓库根目录 `DOMAIN_REFACTORING_REPORT.md`

### 聚合根与实体

| 聚合根 | 职责 | 不变量 |

哈希规范：
1. 表达式原型 JSON → 规范化 → `exprProtoHash`
2. 来源配置快照 JSON → 规范化 → `provenanceConfigHash`
3. 切片 spec → 规范化 → `slice_signature_hash`
4. 任务幂等键 = `provenance:operation:slice_signature_hash:exprProtoHash`

## 3. 处理流程（用例层视角）

### 3.1 Plan Ingestion（计划摄入用例）

1. **调度触发**：XXL-Job 解析参数并记录 `ScheduleInstance`
2. **用例入口**：`ScheduleRunAdapter` 构建 `PlanIngestionCommand` → 调用 `PlanIngestionOrchestrator`
3. **前置验证**：`PlannerValidator` 校验窗口参数合法性、队列压力、来源能力
4. **窗口解析**：`PlanningWindowResolver` 根据 HARVEST/BACKFILL/UPDATE 模式解析时间窗口（校验跨度、齐次对齐、滞后安全）
5. **配置拉取**：远程拉取 provenance 配置 → 快照化 + Hash (`provenanceConfigHash`)
6. **表达式构建**：`PlanExpressionBuilder` 规范化表达式 → 生成 `exprProtoHash`
7. **计划组装**：`PlanAssembler` 协调流程
   - 构建 Plan 蓝图（状态：DRAFT → SLICING）
   - 调用 `SlicePlanner`（TIME/SINGLE 策略）生成 PlanSlice 列表
   - 为每个 Slice 生成 `slice_signature_hash` 并创建 Task
   - 计算任务幂等键：`provenance:operation:slice_signature_hash:exprProtoHash`
8. **持久化**：`PlanRepository` 保存 Plan/Slice/Task（单事务）
9. **Outbox 发布**：`TaskOutboxPublisher` 装配 OutboxMessage 并写入 Outbox 表（同事务）

### 3.2 Outbox Relay（转发用例）

1. **调度触发**：XXL-Job 定时扫描 PENDING 状态的 Outbox 记录
2. **用例入口**：`OutboxRelayJobAdapter` 构建 `OutboxRelayCommand` → 调用 `OutboxRelayOrchestrator`
3. **租约获取**：通过 `pub_lease_owner` + `pub_leased_until` 获取批次租约
4. **计划构建**：`RelayPlanBuilder` 根据租约批次生成转发计划
5. **批量执行**：`OutboxRelayExecutor` 遍历批次
   - 校验 `channel`（通过 `ChannelRegistry`）
   - 发布消息到 RocketMQ
   - 更新状态：PUBLISHED（成功） / 递增 `retry_count`（失败）
6. **错误分类**：`RelayErrorClassifier` 区分 FATAL / TRANSIENT 错误
   - **TRANSIENT**：指数退避更新 `next_retry_at`
   - **FATAL**：超过重试阈值标记 `DEAD`
7. **事件发布**：`RelayEventPublisher` 发布领域事件（如 `OutboxMessagePublishedEvent`）
8. **租约释放**：执行完成后释放租约（清除 `pub_lease_owner`）

**失败策略**：
- 写库前失败 → 直接中断（保持幂等）
- 写库成功但发布失败 → 按照 `retry_count + next_retry_at` 指数退避，超过阈值标记 `DEAD`

## 4. 窗口与切片

窗口模式：

| 模式 | 用途 | 关键参数 | 保护策略 |
| ---- | ---- | -------- | -------- |
| HARVEST | 增量采集 | lookback / alignment / lagSafety | 超近实时截断与滞后缓冲 |
| BACKFILL | 历史回填 | from / to / alignment | 跨度上限控制 |
| UPDATE | 选择性更新 | 输入窗口或游标 | 空或过小窗口跳过 |

切片策略：

| 策略 | 行为 | 场景 |
| ---- | ---- | ---- |
| TIME | 按时间步长拆分多个子窗口 | 大跨度/并行 |
| SINGLE | 不拆分 | 小窗口或轻量操作 |

## 5. 计划装配与错误分类

状态流转：`DRAFT → SLICING → READY / PARTIAL / FAILED / COMPLETED`（执行端更新）。

错误分类：

| 异常类型 | 触发点 | 错误码段 |
| -------- | ------ | -------- |
| PlanValidationException | 窗口非法 / 队列过载 | ING-12xx |
| PlanAssemblyException | 切片为空 / 表达式局部化失败 | ING-13xx |
| PlanPersistenceException | Plan/Slice/Task 写入失败 | ING-14xx |
| OutboxPersistenceException | Outbox 写入失败 | ING-15xx |
| IngestConfigurationException | 远程配置缺失/错误 | ING-16xx |

部分失败策略：切片生成过程中局部异常 → 标记 Plan=PARTIAL，保留成功任务；致命问题直接 FAIL。

## 6. Outbox 与 Relay

核心字段：`channel` / `partition_key` / `dedup_key` / `status_code` / `retry_count` / `next_retry_at` / `pub_lease_owner` / `pub_leased_until`。

发布步骤：
1. 扫描 `status=PENDING` 且租约未占用、`not_before` 已到期的行
2. 设置租约并置为 `PUBLISHING`
3. 发送 MQ：发送前按全局 `ChannelRegistry` 校验 `channel`（小写点分段≥3，可选 domain，白名单）；
   - 成功 -> `PUBLISHED` + `msgId`
   - 失败 -> 计算 `next_retry_at` 回退 `PENDING` 或超过阈值标记 `DEAD`

指数退避：`delay = baseMs * 2^retry`（裁剪上限），避免重试风暴。

分区顺序：推荐使用 `partition_key = provenance:operation`，保障局部有序与受控并发。

## 7. 关键配置项

| Key | 作用 | 示例默认 |
| --- | ---- | -------- |
| `patra.ingest.outbox-relay.enabled` | 是否启用 Relay | `true` |
| `patra.ingest.outbox-relay.batch-size` | 每次扫描条数 | `200` |
| `patra.ingest.outbox-relay.lease-duration` | 租约持续时间 | `PT30S` |
| `patra.ingest.outbox-relay.max-retry` | 最大重试次数 | `8` |
| `patra.ingest.outbox-relay.backoff-base-ms` | 退避基准 | `1000` |
| `patra.ingest.planner.queue-threshold` | 排队任务上限 | `10000` |

## 8. 数据表要点

| 表 | 唯一/幂等键 | 说明 |
| -- | ---------- | ---- |
| `ing_plan` | `uk_plan_key(plan_key)` | 计划蓝图复用与幂等 |
| `ing_plan_slice` | `plan_id + slice_signature_hash` | 防止重复切片生成 |
| `ing_task` | `uk_task_idem(idempotent_key)` | 避免重复任务 |
| `ing_outbox_message` | `channel + dedup_key` | 幂等发布 + 可重试 |
| `ing_schedule_instance` | — | 调度审计与回放 |

索引建议：Outbox 按 `(status_code, not_before, id)` + `(channel, partition_key, status_code)`；Task 按 `(status_code, priority)`。

## 9. 持久化转换器规范

### MapStruct 统一约定
- DO ↔ 聚合/实体映射统一使用 MapStruct 接口；如需 `default` 方法，仅可用于委托本接口静态辅助方法，禁止引入业务分支。
- 通用 JSON、Map 转换统一依赖 `JsonNodeMappings`（位于 `patra-common`），通过 `JsonNodeMappings.jsonStringToNode(...)` 等静态方法完成，避免重复维护 `ObjectMapper`。
- 枚举 code ↔ 枚举体、值对象拆解逻辑统一内联在各个 MapStruct Converter 中的 `@Named` 静态方法，保持映射语义与聚合转换贴合。
- MapStruct 映射禁止依赖 Spring 工具类（如 `StringUtils`），确保 `patra-common` 等基础模块在纯 JDK 环境也能编译。

### Converter 内置静态方法规范
- `TaskConverter`：处理任务状态、租约、时间线拆组，复用 `LeaseInfo` / `ExecutionTimeline` 值对象并负责 JSON ↔ 字符串转换。
- `PlanConverter` / `PlanSliceConverter`：封装计划及切片状态回退、JSON 快照转换，直接调用 `JsonNodeMappings`。
- `TaskRunConverter` / `TaskRunBatchConverter`：将统计、检查点与幂等键解析为领域值对象，集中处理 JSON 解析异常。
- `CursorConverter` / `CursorEventConverter`：统一游标类型、命名空间、方向枚举与血缘信息映射，集中封装 Cursor/CursorEvent 聚合的写入与回放策略。
- `ScheduleInstanceConverter`：处理 Scheduler/TriggerType 字典反解与调度参数 Map ↔ Json 转换。

> 通过上述约定，infra 层 converter 仍保持声明式，同时避免额外 support 包，领域模型演化仅需在对应 Converter 中调整，方便追踪与复用。

## 10. 扩展点

| 扩展点 | 目的 | 做法 |
| ------ | ---- | ---- |
| `SlicePlannerRegistry` | 新增切片策略 | 实现接口 + 注册 Bean |
| `PlanningWindowResolver` | 自定义窗口算法 | 替换默认实现 |
| `PlanExpressionBuilder` | 新 DSL/过滤表达式 | 组合/装饰模式注入 |
| `DestinationBuilder` | 统一 `channel → destination` 解析 | 由 RocketMQ Starter 提供 |
| `ChannelRegistry` | 统一 channel 注册/校验 | 注解/接口注册，`patra.messaging.channels.*` 控制 |
| 领域通道目录 | `ChannelKey` + `IngestChannels` | 去魔法值，适配发送/接收 |
| （可选）装饰 Publisher | 自定义发布策略（延迟/事务/路由） | 覆盖 `PatraMessagePublisher` Bean |
| `IngestErrorMappingContributor` | 扩展错误码映射 | 追加异常映射 |

## 10. 运维排障速查

| 现象 | 排查路径 | 处理建议 |
| ---- | -------- | -------- |
| 无任务生成 | 检查 `ing_plan` 是否新增；查看日志 ING-12xx | 调整窗口或降低队列阈值 |
| Plan 长期 SLICING | 切片生成异常或空集 | 检查切片策略、窗口跨度 |
| Outbox 堆积 | `status=PENDING` 激增 | 查看发布错误码；检查 MQ 可用性 |
| 多条消息 DEAD | 重试次数超限 | 修复问题后人工回放 |
| 发布抖动 | `retry_count` 激增 | 调整退避参数或临时降并发 |

## 11. Roadmap

| 项目 | 优先级 | 价值 |
| ---- | ------ | ---- |
| CURSOR / ID_RANGE 切片策略 | High | 支持非时间序列来源 |
| 指标 & 健康探针 | High | 完成观测性闭环 |
| PlanSlice/Task 批量插入优化 | Mid | 降低写放大 |
| 任务执行端/领取协议 | Mid | 填补执行闭环 |
| Relay 熔断与分级限速 | Low | 避免重试风暴 |

## 12. 快速上手
1. 新增调度：继承 `AbstractProvenanceScheduleJob`，配置 XXL-Job 参数
2. 运行：触发作业 → 查看 `ing_plan` / `ing_plan_slice` / `ing_task` / `ing_outbox_message`
3. 验证发布：观察 Outbox 状态从 PENDING → PUBLISHED
4. 模拟失败：临时阻断 MQ，观察 `retry_count` 与 `next_retry_at`
5. 扩展策略：实现新 `SlicePlanner` 并注册到 `SlicePlannerRegistry`

---

> 文档基于 2025-09-28 代码快照，结构性变更后请同步更新。
