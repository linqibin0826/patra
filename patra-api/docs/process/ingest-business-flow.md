# patra-ingest 业务流程说明（v0.1）

本文面向“计划式文献采集”的业务流转与协作边界，帮助产品/研发/运维在同一语义下沟通与验收。
聚焦端到端流程、关键决策点、幂等与重试策略，以及与配置/观测的映射关系。

## 1. 目标与范围
- 目标：在“采集 → 解析/切片 → 任务生成 → Outbox 发布 → MQ 投递”链路中，保证可回放、可幂等、可观测。
- 范围：仅涵盖 `patra-ingest` 微服务的入站调度、计划编排与 Outbox 转发；下游执行端不在本文范围。
- 不做的事：不定义 domain 规则细节、不引入跨层依赖；不在代码中硬编码配置/密钥。

## 2. 参与者与泳道
- 调度中心（XXL-Job）：按计划触发“计划编排”与“Outbox Relay”。
- Ingest Adapter（入站适配）：解析调度参数、声明消费者等（无业务逻辑）。
- Ingest App（用例编排）：Plan/Relay 应用服务与执行器。
- Ingest Domain（领域）：聚合/值对象/端口与策略，不依赖框架。
- Ingest Infra（基础设施）：仓储、消息发布、配置装配等出站能力。
- MQ（RocketMQ 5.x）：消息中间件，topic 使用 UPPER_SNAKE。
## 3. 端到端总览（TL;DR）
1) 调度触发计划编排：持久化调度实例 → 解析窗口（时间/齐次/滞后）→ 组装 Plan/Slice/Task → Task 入队事件转为 OutboxMessage 持久化。
2) 调度触发 Outbox Relay：扫描 `PENDING` 消息 → 抢占租约 → 发布到 MQ → 成功/失败/延期写回状态。
3) 入站消费者（示例）：订阅 `INGEST_TASK_READY`，日志验证 KEYS/TAGS/partitionKey 与 payload。

关键保障
- 幂等：planKey 去重；Outbox `(channel,dedupKey)` 去重；任务幂等键包含来源/操作/切片签名/表达式哈希。
- 顺序：分区键 `partitionKey=provenance:operation`，保障同“来源+操作”有序。
- 可回放：DEAD/失败消息可回补；窗口策略与游标水位可控。
## 4. 流程 A：计划编排（Plan Ingestion）
入口
- XXL 作业（示例）：按来源/操作触发，传入窗口/优先级等参数。
- 应用服务：`PlanIngestionOrchestrator.ingestPlan(...)`。

步骤（对齐代码 6 阶段）
1) 持久化调度实例 + 拉取 Registry 配置快照（provenance/expr）
2) 查询游标水位（仅前进）→ 解析时间窗口（HARVEST/BACKFILL/UPDATE）
3) 构建计划表达式（快照与哈希，不编译）
4) 前置校验：窗口合理性、队列压力阈值、能力边界
5) 装配蓝图：Plan/PlanSlice/Task（可复用/补偿）
6) 持久化 Plan/Slice/Task → 收集 TaskQueuedEvent → 调用 `TaskOutboxPublisher` 生成 OutboxMessage 并批量写库

产出
- Plan/Slice/Task 持久化；OutboxMessage `status=PENDING`；调度/表达式快照落地。

核心幂等
- planKey 去重命中 → 仅对 FAILED/CANCELLED 任务补偿；Outbox 走 `publishRetry` 刷新/新增。
## 5. 流程 B：Outbox Relay（可靠发布）
入口
- XXL 作业：`ingestOutboxRelayJob`（可选参数 channel/batch/lease/backoff）。
- 应用服务：`OutboxRelayOrchestrator.relay(...)` → `OutboxRelayExecutor.execute(...)`。

步骤
1) fetchPending：按计划条件批量拉取可发消息（支持“单通道或全部通道”）。
2) acquireLease：按（id,version）尝试租约，失败视为“被其它实例抢占”。
3) publish：委派 `OutboxPublisherPort`；当前实现为 `RocketMqOutboxPublisher`（StreamBridge 动态目的地）。
4) 写回：成功 `markPublished`；异常走错误分类（FATAL/TRANSIENT）→ `markFailed` 或 `markDeferred`（并计算 `nextRetryAt`）。
5) 汇总：统计 fetched/published/retried/failed/leaseMissed，发布领域事件用于审计。

错误与重试
- 分类：配置/非法状态/headers 解析失败 → FATAL；网络/下游短故障 → TRANSIENT。
- 退避：指数退避（base/multiplier/max），超过 `maxAttempts` 标记 DEAD。

顺序与去重
- 顺序：`partitionKey=provenance:operation`；分区表达式兜底 `partitionKey→KEYS`。
- 去重：`dedupKey` 注入 RocketMQ KEYS；重复发送由下游幂等处理与 Outbox 状态双重保障。
## 6. 流程 C：入站消费（验证/占位）
- 适配层声明消费者：`ingestTaskReadyConsumer` 订阅 `INGEST_TASK_READY`，打印 headers 与 payload 供联调验证。
- 头部键：`ROCKET_KEYS`（去重键）、`ROCKET_TAGS`（业务标签）、`ROCKET_MQ_TOPIC`（主题）、`partitionKey`（分区）。
- 本阶段不承载业务处理，仅做链路打通与观测。

## 7. 数据与契约（OutboxMessage → MQ）
- channel：UPPER_SNAKE（示例：INGEST_TASK_READY）。
- payload：JSON 字符串（包含 taskId/planId/sliceId/provenance/operation/idempotentKey/计划窗口等）。
- headers：
  - `ROCKET_KEYS` ← dedupKey
  - `ROCKET_TAGS` ← opType
  - `partitionKey` ← 分区键（为空回退 dedupKey）
  - 追踪头（scheduleInstanceId/scheduler/...），不传业务数据
- msgId：当前阶段无法从 StreamBridge 获取，下游写回为后续里程碑。
## 8. 幂等与一致性策略
- 计划级：planKey（来源/操作/窗口/表达式）去重，命中则按 FAILED/CANCELLED 任务补偿。
- 任务级：幂等键 `provenance:operation:sliceHash:exprHash`，避免重复生成。
- Outbox 级：`(channel,dedupKey)` 查找更新；租约 + 版本号自增保障并发安全。
- 事务边界：Plan/Slice/Task 与 Outbox 写入位于同服务内，relay 发布与状态回写属于另一个调度事务。

## 9. 配置要点（摘）
- Binder：`spring.cloud.stream.defaultBinder=rocketmq`；`name-server`/`endpoint` 二选一。
- 动态目的地：生产端使用 `StreamBridge.send(channel, msg)`，不预定义 out binding。
- 分区策略：`partition-key-expression= headers['partitionKey'] ?: headers['KEYS']`；`partition-count=8` ≤ 主题队列数。
- 白名单：`papertrace.ingest.outbox.strict-channel-whitelist` + `allowed-channels`，strict=true 且列表空 → 启动 Fail Fast。
- Relay：`patra.ingest.outbox-relay.*`（batchSize/leaseDuration/maxAttempts/backoff...）。
## 10. 观测与运维
- 指标建议：`ingest.plan.created`、`ingest.plan.slice.count`、`ingest.outbox.publish.duration`。
- 日志字段：planKey、sliceSignatureHash、taskIdempotentKey、traceId、leaseOwner、retryCount。
- 常见排障：
  - 无任务生成 → 查 `ing_plan` 与 ING-12xx 日志；检查窗口与阈值。
  - Outbox 堆积 → 查租约是否超时、`nextRetryAt` 与错误码。
  - 发布失败 → 检查白名单/通道可用性、退避参数与 RocketMQ 连接。

## 11. 验收清单（给 QA/联调）
- 计划编排：命中/不命中去重用例；窗口边界/滞后/齐次策略生效。
- Outbox 发布：published/failed/retried/leaseMissed 统计符合预期；FATAL 与 TRANSIENT 路径区分清晰。
- MQ 验证：消费者正确打印 headers（ROCKET_KEYS/TAGS/partitionKey）与 payload；分区键兜底逻辑生效。
- 配置健壮性：strict 白名单 + 空列表触发启动失败；动态目的地无占位 topic。
## 12. 主要入口与代码参考
- 计划编排：`patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java`
- Outbox 发布（执行器）：`patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/executor/OutboxRelayExecutor.java`
- RocketMQ 发布实现：`patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/messaging/RocketMqOutboxPublisher.java`
- 入站消费者：`patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java`
- 关键配置：`patra-ingest/patra-ingest-boot/src/main/resources/ingest-mq-config.yaml`

> 补充阅读：`patra-ingest/README.md`（模块综述）、`docs/modules/ingest/rocketmq-stream.md`（RocketMQ 接入指南）、`docs/process/ingest-dataflow.md`（端到端数据流）。
