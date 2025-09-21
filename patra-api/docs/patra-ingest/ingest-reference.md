# Ingest · Reference（表结构要点 / 状态机 / 幂等键）
导航： `docs/patra-ingest/README.md` ｜ 同域： `docs/patra-ingest/ingest-schema-design.md` ｜ `docs/patra-ingest/ingest-guide.md` ｜ `docs/patra-ingest/ingest-ops.md` ｜ DDL：`docs/patra-ingest/patra-ingest.sql`

本文聚焦表清单与关键字段、索引与唯一性约束、状态机与幂等键，作为日常查阅手册。完整字段定义以 DDL 为准。

## 表清单与要点

- `ing_schedule_instance`（调度实例）
  - 用途：一次外部调度触发的“根”，固化：`trigger_params`、`provenance_config_snapshot`、`expr_proto_snapshot`。
  - 关键字段：`scheduler_code`、`trigger_type_code`、`provenance_code`、`expr_proto_hash`。
  - 索引：`idx_sched_src(scheduler_code, scheduler_job_id, scheduler_log_id)`；审计索引若干。

- `ing_plan`（计划蓝图）
  - 用途：定义“本次总窗口 + 切片策略”；不直接执行。
  - 关键字段：`schedule_instance_id`、`provenance_code`、`operation_code`、`slice_strategy_code`、`expr_proto_hash`、`window_from/window_to`、`status_code`。
  - 索引：按 `status_code/created_at`、`provenance_code/operation_code` 组合检索。

- `ing_plan_slice`（切片计划）
  - 用途：把 plan 的总窗切成多个可并行执行的 slice；保存 `slice_signature` 与局部化表达式 `expr_hash/expr_snapshot`。
  - 关键字段：`plan_id`、`slice_no/total_slices`、`slice_signature`、`expr_hash`、`status_code`。
  - 索引：`idx_slice_plan_status(plan_id, status_code)` 等。

- `ing_task`（任务）
  - 用途：每个 slice 派生一个任务；绑定来源/操作/凭据/参数；形成强幂等键。
  - 关键字段：`plan_id/slice_id`、`provenance_code`、`operation_code`、`credential_id`、`params`、`idempotent_key`、`expr_hash`、`priority`、`status_code`、`scheduled_at/start/finish`。
  - 唯一约束：`uk_task_idem(idempotent_key)`。
  - 索引：`idx_task_slice(slice_id, status_code)`、`idx_task_src_op(provenance_code, operation_code, status_code)`、`idx_task_sched_at(status_code, scheduled_at)`、`idx_task_cred(credential_id)`。

- `ing_task_run`（运行尝试）
  - 用途：任务的具体一次尝试；失败不覆盖 task，只在 run 记录。
  - 关键字段：`task_id`、`attempt_no`、`status_code`、`checkpoint`、`stats`、`error`、`window_from/to`、`started/finished`。
  - 唯一约束：`uk_run_attempt(task_id, attempt_no)`。
  - 索引：`idx_run_task_status(task_id, status_code, started_at)`。

- `ing_task_run_batch`（运行批次）
  - 用途：run 的分页/令牌步进账目；断点续跑与去重的最小单元。
  - 关键字段：`run_id`、`batch_no/page_no/page_size`、`before_token/after_token`、`idempotent_key`、`record_count`、`status_code`、`error`、`started/finished`。
  - 唯一约束：`uk_run_batch_no(run_id, batch_no)`、`uk_run_before_tok(run_id, before_token)`、`uk_batch_idem(idempotent_key)`（具体命名见 DDL）。
  - 索引：按 `status_code/started_at`、冗余 lineage 字段（`task_id/slice_id/plan_id/expr_hash`）。

- `ing_cursor_event`（水位推进事件）
  - 用途：append-only 不可变账，记录每次推进前后值、窗口与 lineage；用于回放与审计。
  - 关键字段：`provenance_code`、`operation_code`、`cursor_key`、`namespace_scope_code`、`namespace_key`、`cursor_type_code`、`prev/new_*`、`window_from/to`、`direction_code`、`idempotent_key`、lineage 冗余。
  - 唯一约束：`uk_cur_evt_idem(idempotent_key)`。
  - 索引：时间线/窗口/类型与数值索引、lineage 检索。

- `ing_cursor`（当前水位）
  - 用途：某命名空间下当前水位的“最新值”，供增量任务启始使用。
  - 关键字段：与 `ing_cursor_event` 相同维度上的当前值 + 乐观锁 `version`。
  - 约束：仅允许“前进”；由应用以 CAS/WHERE 保障（见 DDL 中 version/updated_at 条件更新）。

## 状态机（建议枚举）

- `ing_task.status_code`：`QUEUED | RUNNING | SUCCEEDED | FAILED | CANCELLED`
- `ing_task_run.status_code`：`PLANNED | RUNNING | SUCCEEDED | FAILED | CANCELLED`
- `ing_task_run_batch.status_code`：`RUNNING | SUCCEEDED | FAILED | SKIPPED`

各状态码来自 Registry 字典体系（type 均以 ing_* 开头），可拓展但需保持单调语义。

## 幂等键定义（建议实现）

- Task：`SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))`
- Batch：`SHA256(run_id + before_token | page_no)`
- Cursor 事件：`SHA256(source,op,key,ns_scope,ns_key,prev->new,ingestWindow,run_id,...)`

参数规范化建议：键排序、剔除空/默认值、数值/时间采用标准化字符串，确保跨语言/版本稳定。

## 依赖的字典类型（与 Registry 对齐）

- `ing_scheduler`、`ing_trigger_type`
- `ing_operation`（HARVEST/BACKFILL/UPDATE/METRICS …）
- `ing_slice_strategy`（TIME/ID/CURSOR/BUDGET …）
- `ing_task_status`、`ing_task_run_status`、`ing_batch_status`
- `ing_namespace_scope`（GLOBAL/EXPR/CUSTOM …）
- `ing_cursor_type`（TIME/ID/TOKEN）与 `ing_cursor_direction`（FORWARD/BACKFILL）

具体取值请参见 Registry 的字典文档与数据：`docs/patra-registry/dict/*` 与 DDL `docs/patra-registry/patra-registry.sql`。
