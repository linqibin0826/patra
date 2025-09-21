# Papertrace · Patra Ingest 文档体系总览

本目录汇集 Ingest（采集编排执行）相关设计与 SQL，按“Guide → Reference → Ops → SQL”的脉络组织，支持顺序阅读与交叉跳转。

## 导航（推荐阅读顺序）

- 1) Ingest Schema 总览（编排语义与关系）
  - 目录索引：`docs/patra-ingest/ingest-schema-design.md`
- 2) Guide（入门 + 设计意图 + 使用）
  - 分篇：`docs/patra-ingest/ingest-guide.md`
- 3) Reference（表结构要点 + 状态机 + 幂等键）
  - 分篇：`docs/patra-ingest/ingest-reference.md`
- 4) Ops / Runbook（运维与排障）
  - 分篇：`docs/patra-ingest/ingest-ops.md`
- 5) 建表 SQL（整套 DDL + 索引）
  - 文件：`docs/patra-ingest/patra-ingest.sql`

## 关系图（高层语义）

- 调度触发 → `ing_schedule_instance`：固化“触发入参 + 来源配置快照 + 表达式原型”。
- 生成计划 → `ing_plan`：定义总目标窗口与切片策略（time/id/cursor/预算）。
- 切片计划 → `ing_plan_slice`：把总窗切分为多个可并行且幂等的 slice；派生局部化表达式。
- 派生任务 → `ing_task`：每个 slice 生成 1 个任务，绑定来源/操作/凭据/参数，并形成强幂等键。
- 任务运行 → `ing_task_run`：一次具体尝试（首次/重试/回放）；失败不覆盖 task，仅记录于 run。
- 运行批次 → `ing_task_run_batch`：分页/令牌步进的最小账目，承载断点续跑与去重。
- 推进水位 → `ing_cursor_event`（事件账）与 `ing_cursor`（当前值，仅最新）。

## 快速入口

- 总览：`docs/patra-ingest/ingest-schema-design.md`
- 入门：`docs/patra-ingest/ingest-guide.md`
- 参考：`docs/patra-ingest/ingest-reference.md`
- 运维：`docs/patra-ingest/ingest-ops.md`
- 全部 DDL：`docs/patra-ingest/patra-ingest.sql`

---

提示：文档采用“高内聚长文”风格，避免碎片化；与 Registry 文档在风格与术语上保持一致。
