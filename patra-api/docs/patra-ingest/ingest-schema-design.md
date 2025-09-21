# Ingest · Schema 总览
导航： `docs/patra-ingest/README.md` ｜ 同域： `docs/patra-ingest/ingest-guide.md` ｜ `docs/patra-ingest/ingest-reference.md` ｜ `docs/patra-ingest/ingest-ops.md` ｜ DDL：`docs/patra-ingest/patra-ingest.sql`

本文给出 Ingest（采集）域的核心模型、语义边界与关系映射，帮助你在“调度 → 计划 → 切片 → 任务 → 运行 → 批次 → 水位”的链路中建立统一心智模型。

## 流程全景（语义到表）

- 调度触发 → `ing_schedule_instance`
  - 来自外部调度器（如 XXL-Job）的一次触发，固化调度参数、来源配置快照、表达式原型（未局部化）。
  - 这是一次“采集批次”的根，后续所有 plan/slice/task/run 都关联于此上下文（逻辑外键，不建物理 FK）。
- 生成计划 → `ing_plan`
  - 定义本次总的采集目标窗口（时间/ID/游标/预算）与切片策略（time/id/cursor/budget）。
  - plan 是蓝图，不直接执行，仅描述要做什么与如何切片。
- 切片计划 → `ing_plan_slice`
  - 根据 plan 的总窗与策略切成多个 slice；每个 slice 是并行执行与幂等的最小单元。
  - 保存 `slice_signature`（边界 JSON）与局部化表达式（expr 局部化为本 slice 的窗口）。
- 派生任务 → `ing_task`
  - 每个 slice 派生 1 个 task，绑定来源 code、操作类型（harvest/backfill/update…）、凭据、参数等。
  - 生成强幂等 `idempotent_key`，确保“同 slice+操作+参数+触发上下文”仅一条 task。
- 任务运行（尝试） → `ing_task_run`
  - 单个 task 可有多次 run（首次、失败重试、人工回放）。
  - run 记录自己的状态、checkpoint、统计等；失败不覆盖 task 状态（run 是一次具体尝试）。
- 运行批次 → `ing_task_run_batch`
  - run 执行时按页码/游标步进；每次步进即一个 batch，是断点续跑与去重的最小账目。
  - 记录 before_token/after_token、页号、记录数、状态，并以幂等键防止重复处理。
- 推进游标（水位） → `ing_cursor_event` / `ing_cursor`
  - run 成功覆盖一个窗口后，写入 append-only 的 `ing_cursor_event`（不可变历史账）。
  - 然后用乐观锁推进 `ing_cursor` 当前值（只允许前进），为后续增量的起点提供水位。

## 核心约束与设计意图

- 去 FK 化：跨模块（Registry ↔ Ingest）与内域均不建物理外键，完整性由应用保障，并辅以必要的二级索引。
- 幂等优先：
  - `ing_task.idempotent_key`：slice 边界 + expr_hash + operation + trigger + 规范化参数。
  - `ing_task_run_batch.idempotent_key`：run + before_token|page_no → 保证批次账目的去重。
  - `ing_cursor_event.idempotent_key`：source/op/key/ns_scope/ns_key + 前后值 + 窗口 + lineage。
- 明确状态机：任务与运行均具备最小充分状态（QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED 等）。
- 时间区间语义：窗口采用 [from, to) 半开区间；`window_to` 不包含，避免重叠。
- 命名与字典：所有 code 字段依赖 Registry 的字典表（如 http_method、ing_operation、ing_batch_status 等）。

## 与 Registry 的关系

- 来源配置快照：`ing_schedule_instance.provenance_config_snapshot` 来自对 `reg_prov_*` 的读侧装配；不与具体 reg 表建立 FK。
- 来源 code：Ingest 内部使用 `provenance_code` 与 Registry 的 `reg_provenance.provenance_code` 对齐（逻辑关联）。
- 凭据：`ing_task.credential_id` 可引用 `reg_prov_credential.id`（逻辑维度，仍不建 FK）。

## 常见切片模式

- 时间切片（time-based）：按固定时间窗切分（例如 5 分钟、1 小时）。
- ID 切片（id-range）：按 ID 区间切片（如数值自增 ID/哈希分桶）。
- 游标切片（cursor/token）：根据来源接口的游标推进，批次内承载断点续跑。
- 预算切片（record budget）：按预算配额切片（如每 slice 限定预估记录数/调用配额）。

## 读写侧示例（抽象）

- 读侧：
  - 查找待执行任务：按 `status_code=QUEUED` 且 `scheduled_at<=NOW()`。
  - 批次去重：插入 `ing_task_run_batch` 前先计算幂等键，冲突则跳过/对齐状态。
- 写侧：
  - 灰度开关由 Plan/Slice 生成逻辑控制；同一 slice 不重复派生 Task（靠 uk + CAS）。
  - 光标推进：先 append 事件，再乐观锁更新 `ing_cursor` 当前值（仅前进）。

更多字段与索引详解，参见：`docs/patra-ingest/ingest-reference.md` 与 `docs/patra-ingest/patra-ingest.sql`。
