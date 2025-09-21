# Ingest · Ops / Runbook（运维与排障）
导航： `docs/patra-ingest/README.md` ｜ 同域： `docs/patra-ingest/ingest-schema-design.md` ｜ `docs/patra-ingest/ingest-guide.md` ｜ `docs/patra-ingest/ingest-reference.md`

本文提供落地运维指南：初始化、调度对接、回放与重试、健康检查、巡检与常见问题。

## 初始化与巡检

- 初始化数据库对象：执行 `docs/patra-ingest/patra-ingest.sql`。
- 对齐字典：确保 Registry 已写入 ingest 相关字典项（ing_scheduler/ing_operation/…）。
- 基础巡检：
  - 表是否存在且字符集/排序规则正确（utf8mb4_0900_ai_ci）。
  - 核心唯一键是否生效（如 `uk_task_idem`、`uk_run_attempt`、`uk_batch_idem` 等）。

## 与调度器对接（建议）

- 外部调度器（如 XXL-Job）回调应用 → 应用写入 `ing_schedule_instance`：
  - `scheduler_code`、`scheduler_job_id/log_id`、`trigger_type_code`、`trigger_params`。
  - 装配 `provenance_config_snapshot`（由 Registry 的 reg_prov_* 读侧聚合）。
- 计划与切片生成：
  - 由应用根据 `trigger_params` 产生 `ing_plan`，再切片生成 `ing_plan_slice`。
  - 按幂等策略派生 `ing_task`，避免重复。

## 任务执行与重试策略

- 拉取待执行任务：`status_code=QUEUED AND scheduled_at<=NOW()`，按 `priority`、`scheduled_at` 排序。
- 运行开始：创建 `ing_task_run`（attempt_no=上一 run + 1，或 1），置 `RUNNING` 并落心跳 `last_heartbeat`。
- 分页处理：每页/每令牌推进写 `ing_task_run_batch`；根据来源响应填写 `before_token/after_token` 与 `record_count`。
- 成功完成：所有 batch `SUCCEEDED` 后，置 run `SUCCEEDED`，并将 task 标记 `SUCCEEDED`。
- 失败处理：
  - 单批失败：记录 `error` 与 `status_code=FAILED`，允许对失败批进行局部重试（幂等键防重复）。
  - 整体重试：新建 run，沿 `checkpoint` 或“最后成功 batch 的 after_token/page_no+1”继续。
  - 回放：必要时可重新派生相同 slice 的 task（相同参数 → 幂等键命中 → 不产生重复任务）。

## 水位推进与回放

- 成功 run 完成后：
  - 先 append `ing_cursor_event`：记录 `prev/new`、窗口、方向与 lineage。
  - 再乐观锁更新 `ing_cursor` 当前值：仅允许“前进”。
- 回放：
  - 遍历 `ing_cursor_event` 可重建水位时间线；必要时做“前滚重算”。
  - 如需回退，务必通过新增 BACKFILL 方向的事件记录体现，不直接回写 `ing_cursor`。

## 健康检查与告警

- 队列滞留：`ing_task` QUEUED 超时未启动（`scheduled_at` 超期）→ 告警。
- 运行超时：`ing_task_run` 长时间 RUNNING 或 `last_heartbeat` 停滞 → 告警与抢占恢复策略。
- 批次重试风暴：同一 `idempotent_key` 冲突过多 → 识别来源异常/限流/配额问题。
- 光标停滞：某来源/操作/命名空间下 `ing_cursor` 长时间不前进 → 告警。

## 常见问题（FAQ）

- Q：为什么不建外键？
  - A：跨模块与执行域高并发下，外键放大耦合与锁竞争；以应用层约束 + 唯一键 + 幂等策略更稳健。
- Q：如何避免重复处理同一页/位置？
  - A：用 `ing_task_run_batch.idempotent_key`（run_id + before_token | page_no）作为最小账目去重。
- Q：表达式如何局部化？
  - A：在切片时将表达式原型注入 slice 的边界，生成 `expr_snapshot/expr_hash`，后续 task/run/batch 冗余传递。
- Q：如何“只前进不回退”地更新光标？
  - A：采用 WHERE 条件带 `version`/`updated_at` 的 CAS；或比较新旧值的单调性（时间/数值）。

更多实践建议参见：`docs/patra-ingest/ingest-guide.md` 与 DDL：`docs/patra-ingest/patra-ingest.sql`。
