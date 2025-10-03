# 采集链路（Ingest）端到端指南

本文覆盖从调度触发到消息出站的完整流程，帮助开发者理解调试入口与排障关键点。

## 1. 前置条件
- Nacos 已同步 provenance/expr 配置
- Registry 服务运行并对外暴露 API
- MySQL、XXL-Job 稳定可用；消息通道（如 MQ）按部署选配

## 2. 调度与上下文
1. XXL-Job 调度中心触发作业，传入 `provenanceCode`、`operationCode`、窗口参数
2. `patra-ingest-adapter` 解析调度参数，构造 `ScheduleInstance` 并调用 `app.planning` 的 `PlanIngestionApplicationService`
3. 记录调度日志，确保 `schedule_instance` 表可追溯

## 3. 配置拉取与规范化
1. 通过 Feign 调用 `patra-registry` 获取 Provenance 配置快照
2. 同步获取表达式能力/渲染规则快照
3. 使用 `JsonNormalizer` 生成哈希（exprProtoHash / provenanceConfigHash），保障幂等

## 4. 窗口解析与切片
- 根据操作类型选择 HARVEST / BACKFILL / UPDATE 策略
- 校验窗口跨度、滞后安全（lagSafety）与齐次对齐（alignment）
- 通过 `app.planning.slice.SlicePlannerRegistry` 生成 PlanSlice（TIME / SINGLE 等），写入 `ing_plan_slice`

## 5. 任务装配与持久化
1. 构建 Task（幂等键：`provenance:operation:slice_signature_hash:exprProtoHash`）
2. 同事务写入 Plan / Slice / Task / OutboxMessage（`app.planning.outbox.TaskOutboxPublisher` 负责挂载 Outbox 记录）
3. 失败即整体回滚，成功记录 Plan 状态 `READY`

## 6. Outbox 发布
1. Relay 任务（`app.relay.OutboxRelayExecutor`）扫描 `status=PENDING` 且租约未占用的消息
2. 获取租约后委托 `OutboxPublisherPort` 发布；当前默认实现 `NoopOutboxPublisher`，仅记录日志并返回空 msgId，后续可替换为 MQ/Webhook 等具体实现
3. 失败根据 `retry_count` 动态计算 `next_retry_at`，超过阈值标记 `DEAD`

## 7. 观测与排障
- 指标：`ingest.plan.created`、`ingest.outbox.publish.duration`
- 日志关键字段：`planKey`、`sliceSignatureHash`、`traceId`
- 常见问题
  - **配置缺失**：Registry 返回 404 → 调整配置或回放
  - **窗口异常**：ING-12xx → 检查跨度、滞后、队列阈值
  - **Outbox 堆积**：查看消息发布日志与租约是否超时

## 8. 回放流程（建议）
1. 手动将 `DEAD` 消息状态改为 `PENDING` 并清空租约字段
2. 重新触发 Relay 任务或调用管理 API
3. 观察消息重发情况与消费者处理结果

## 9. 相关文档
- 模块 README：`patra-ingest/README.md`
- 错误规范：`docs/standards/cross-service-error-best-practices.md`
- Starters 指南：各 Starter 模块 README（`patra-spring-boot-starter-*`、`patra-spring-cloud-starter-feign`）
