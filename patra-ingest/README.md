# patra-ingest

计划式文献采集与任务装配引擎，负责调度解析、切片、任务生成与 Outbox 发布。

## 最近更新（2025-10-02）

### 🔧 代码优化
- **合并 Outbox 查询方法**：将 `fetchPending` 和 `fetchPendingAllChannels` 合并为单一方法，通过 `channel` 参数是否为 `null` 来控制查询范围
  - 接口层：`OutboxRelayStore.fetchPending(String channel, Instant availableTime, int limit)`
  - Mapper 层：使用 MyBatis 动态 SQL（`<if>` 和 `<choose>`）实现条件装配
  - 优势：减少代码重复，提升可维护性，保持 SQL 性能
  
### 🐛 Bug 修复
- **修复 RocketMqOutboxPublisher 的 channel 获取逻辑**
  - **问题**：错误地从 `plan.channel()` 获取 channel，导致查询所有频道时出现 NPE
  - **修复**：改为从消息本身（`message.getChannel()`）获取 channel，确保数据一致性
  - **格式处理**：在 `Channel` 类中新增 `fromString()` 静态工厂方法，自动将大写格式（如 `INGEST.TASK.READY`）转换为小写格式（`ingest.task.ready`）

### ✨ 基础设施增强
- **Channel 类改进** (`patra-spring-boot-starter-rocketmq`)
  - 修改正则表达式从 `[a-z0-9]` 改为 `[A-Z0-9]`，**仅支持大写格式**
  - 新增 `Channel.fromString(String channelValue)` 静态工厂方法
  - 与系统约定一致：全平台统一使用大写格式（如 `INGEST.TASK.READY`）

## 1. 模块定位
- **服务/组件作用**：围绕来源 (provenance) 与操作 (operation) 生成采集计划，保证链路幂等、可回放、可观测
- **主要消费者**：调度中心（XXL-Job）、下游解析/清洗服务、RocketMQ 事件订阅方
- **架构边界**：遵循六边形分层——`adapter`(Inbound：调度/监听，仅入站)、`app`(Planning/Relay 用例编排)、`domain`(Plan/Task 聚合/领域端口)、`infra`(Outbound：仓储/消息/RPC)、`boot`(启动装配)

## 2. 核心能力
- **窗口策略**：HARVEST / BACKFILL / UPDATE，控制齐次校验与滞后安全
- **切片策略**：TIME/SINGLE，可通过 `SlicePlannerRegistry` 扩展
- **计划装配**：Plan → PlanSlice → Task 原子生成，支持部分失败降级
- **幂等体系**：表达式、配置、切片规范化哈希；任务幂等键 `provenance:operation:sliceHash:exprHash`
- **Outbox 发布**：租约 + 指数退避 + 分区顺序，确保可靠投递与可重试

> 深度说明与表格详见 `docs/modules/ingest/deep-dive.md`。

## 3. 分层结构与依赖
- 子模块概览：
  | 子模块 | 职责 |
  |--------|------|
  | `patra-ingest-api` | 错误码、外部 DTO |
  | `patra-ingest-adapter` | XXL-Job 入站（Inbound Only）、Outbox Relay 调度入口 |
  | `patra-ingest-app` | **用例层**：`usecase/plan`（计划编排用例）与 `usecase/relay`（Outbox 转发用例） |
  | `patra-ingest-domain` | Plan/PlanSlice/Task/Schedule 聚合与端口<br>**包结构**（v0.1.0 重构）：<br>- `domain/event/` 统一事件目录<br>- `domain/model/aggregate/` 聚合根（保留 Aggregate 后缀）<br>- `domain/model/vo/` 值对象（含 PlanTriggerNorm） |
  | `patra-ingest-infra` | MyBatis-Plus DO、Mapper、仓储实现、RPC 出站（`infra.rpc.registry.*`） |
  | `patra-ingest-boot` | Spring Boot 启动、错误码映射 |

- **app 层架构**（遵循 DDD + 六边形架构）：
  ```
  app/
  ├── config/                       # 应用层配置
  └── usecase/                      # 用例层
      ├── plan/                     # 计划编排用例
      │   ├── PlanIngestionUseCase.java          # 用例接口
      │   ├── PlanIngestionOrchestrator.java     # 编排器
      │   ├── command/              # 命令对象
      │   ├── dto/                  # 结果 DTO
      │   ├── assembler/            # 计划组装器
      │   ├── slicer/               # 切片策略
      │   ├── window/               # 窗口解析
      │   ├── expression/           # 表达式构建
      │   ├── validator/            # 前置验证
      │   └── publisher/            # Outbox 发布
      │
      └── relay/                    # Outbox 转发用例
          ├── OutboxRelayUseCase.java            # 用例接口
          ├── OutboxRelayOrchestrator.java       # 编排器
          ├── command/              # 命令对象
          ├── dto/                  # 结果 DTO
          ├── executor/             # 转发执行器
          ├── planner/              # 计划构建
          ├── policy/               # 错误分类策略
          ├── publisher/            # 事件发布
          ├── config/               # Relay 配置
          └── support/              # 支持工具
  ```

- 关键依赖：`patra-common`、`patra-expr-kernel`、MyBatis-Plus、RocketMQ、XXL-Job、Nacos
- 禁止事项：在 domain 层引入框架；在 adapter 中写业务逻辑

## 4. 运行与配置
- **调度入口**：继承 `AbstractProvenanceScheduleJob`，由 XXL-Job 触发；参数包含 `provenanceCode`、`operationCode`、窗口定义
- **配置来源**：通过 `patra-registry` 拉取 provenance/expr 快照；本地配置使用 Nacos
- **Outbox 属性**（摘录）：
  | Key | 默认 | 说明 |
  |-----|------|------|
  | `patra.ingest.outbox-relay.enabled` | `true` | 是否启用 Relay |
  | `patra.ingest.outbox-relay.batch-size` | `200` | 扫描批次 |
  | `patra.ingest.outbox-relay.max-retry` | `8` | 最大重试次数 |
  | `patra.ingest.planner.queue-threshold` | `10000` | 队列压力阈值 |

## 5. 观测与运维
- 推荐指标：`ingest.plan.created`、`ingest.plan.slice.count`、`ingest.outbox.publish.duration`
- 日志关键字段：`planKey`、`sliceSignatureHash`、`taskIdempotentKey`、`traceId`
- 运维速查：
  | 现象 | 排查路径 |
  |------|----------|
  | 无任务生成 | 查看 `ing_plan` 表，关注 ING-12xx 日志 |
  | Outbox 堆积 | 查询 `ing_outbox_message` 状态，分析重试/死信 |
  | 发布抖动 | 检查 `retry_count` 与 MQ 可用性，必要时调节 backoff |

## 6. 测试策略
- Domain：验证 Plan/PlanSlice/Task 聚合不变量、幂等键生成
- App：对窗口解析、切片组合、部分失败场景编写用例
- Adapter：调度参数解析、Outbox Relay 调度流程模拟
- Infra：仓储持久化、批量插入、索引策略
- 集成建议：模拟 MQ 不可用、配置缺失、窗口跨越上限等异常

## 7. Roadmap 与风险
| 项目 | 优先级 | 风险/备注 |
|------|--------|-----------|
| CURSOR / ID_RANGE 切片策略 | High | 需定义新幂等键与切片拆分逻辑 |
| 指标与健康探针 | High | 未落地前难以及时发现堆积与租约异常 |
| 批量插入优化 | Mid | 需验证数据库锁与写放大影响 |
| 任务执行端协议 | Mid | 执行链路尚未闭环，需明确消息规范 |
| Relay 熔断/限速 | Low | 防重试风暴，需结合监控阈值 |

主要风险：配置不一致、窗口越界、Outbox 重试风暴、MQ 不可用。建议配合 `docs/process/ingest-dataflow.md` 端到端排查。

## 8. 参考资料
- 深入文档：`docs/modules/ingest/deep-dive.md`
- 端到端流程：`docs/process/ingest-dataflow.md`
- Registry 配置：`docs/modules/registry/deep-dive.md`
- 错误规范：`docs/standards/platform-error-handling.md`
