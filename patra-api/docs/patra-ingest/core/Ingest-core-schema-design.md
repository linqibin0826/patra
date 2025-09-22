# Ingest · Core Schema 设计索引

导航：Core[本页] | Guide | Reference | Ops | Examples ｜ 总览：`../README.md` ｜ 术语：`../GLOSSARY.md`

> 本文是“对象/生命周期/快照”总览，策略选择、限流/预算、游标推进、错误自愈分别见：`../strategy/*`、`../cursor/*`、`../runtime/*`。

## 1. 背景与问题（摘录）
医学文献来源（PubMed / Crossref 等）在分页、时间窗口、限流、鉴权、字段模型上高度异构；同时存在三类主要操作：增量(HARVEST)、历史回填(BACKFILL)、刷新/纠错(UPDATE)。如果将差异直接散落在执行逻辑中，将导致：
- 配置漂移影响执行确定性（运行中 Registry 改动）
- 不统一的窗口与分页导致重复/漏抓
- 缺少批次级“黑匣子”难以重放定位
- HARVEST 与 BACKFILL 并行时游标互相干扰风险

## 2. 目标与非目标
### 2.1 目标
1. 用“调度根 → 计划 → 切片 → 任务 → 运行 → 批次”标准化生命周期；任何执行上下文都可通过 lineage 还原。
2. 双层快照（Plan Proto / Slice Localized）隔离 Registry 配置漂移。
3. 三层幂等（Task / Batch / CursorEvent）+ 业务唯一键，支持精确重放与重复调用无副作用。
4. 支撑三类操作的并行与隔离（命名空间 + direction），不相互回退。
5. 计划与执行解耦：Planner 与 Executor 可独立扩缩容与灰度。
### 2.2 非目标
- 不涉及具体 HTTP 传输实现细节（Transport/鉴权签名见 Strategy）。
- 不覆盖完整观测指标埋点（详见 Runtime Observability）。
- 不设计 Outbox 表结构（假定已有统一事件发布机制）。

## 3. 分层与端口（Core 视角）
| 层 | 职责 (Core 关注点) | 典型端口 |
|----|--------------------|----------|
| ADAPTER | 触发器 / REST / MQ | PlannerTrigger / ExecutorTrigger |
| APP | 用例编排、事务、领域事件 | PlanAppService / TaskAppService |
| DOMAIN | 聚合与领域事件（保持纯粹） | PlanAggregate / SliceAggregate 等 |
| INFRA | Repository / QueryPort / Outbox 持久化 | PlanRepository / TaskRepository |
| STARTER | 通用表达式 / 错误解析 / HTTP 等 | ExprCompilerPort / TransportPort |

依赖方向：ADAPTER→APP→DOMAIN；INFRA→DOMAIN；APP/INFRA 共同只依赖无状态 contract/api。

## 4. 生命周期与关系
```
scheduler trigger
	↓ (schedule_instance)
plan (window + strategy + proto snapshot)
	↓ slicing
plan_slice (localized expr + slice_spec)
	↓ 1:1
task (QUEUED; params=normalized(exec args))
	↓ attempt
task_run (attempt_no; checkpoint; stats)
	↓ loop
task_run_batch (分页/令牌/时间推进最小账目)
	↓ success boundary
cursor_event → cursor (事件先行, 仅前进)
```

### 4.1 核心对象表对应 (更新：对齐 `sql/patra-ingest.sql` 2025-09-21 冗余字段与索引)
| 概念 | 表 | 关键字段(节选) | 状态机(详见 runtime-reference) |
|------|----|----------------|--------------------------------|
| 调度根 | ing_schedule_instance | provenance_code, trigger_params, expr_proto_hash/snapshot | - |
| 计划 | ing_plan | plan_key, provenance_code, endpoint_name, operation_code, window_from/to, slice_strategy_code, slice_params, expr_proto_hash | DRAFT→... |
| 切片 | ing_plan_slice | plan_id, provenance_code, slice_no, slice_signature_hash, slice_spec, expr_hash | PENDING→... |
| 任务 | ing_task | provenance_code, operation_code, credential_id, params, idempotent_key, lease_*, priority, expr_hash | QUEUED→... |
| 运行 | ing_task_run | task_id, attempt_no, provenance_code, operation_code, checkpoint, stats, window_from/to | PLANNED→... |
| 批次 | ing_task_run_batch | run_id, batch_no, page_no, before_token/after_token, provenance_code, operation_code, idempotent_key, record_count | RUNNING→... |
| 游标事件 | ing_cursor_event | provenance_code, operation_code, cursor_key, namespace_scope_code/key, direction_code, idempotent_key | append-only |
| 游标现值 | ing_cursor | provenance_code, operation_code, cursor_key, namespace_scope_code/key, version | - 仅前进 |

### 4.2 冗余字段与索引设计要点
> 新增冗余：`provenance_code` 现已存在于 plan / plan_slice / task / task_run / task_run_batch（cursor_event & cursor 原本就有），用于：
> 1) 减少回溯 lineage 多跳 join；2) 直接按 (provenance_code, operation_code, status_code, 时间) 聚合；3) 与 Registry `reg_provenance.provenance_code` 逻辑对齐（无物理 FK）。

关键复合索引（节选，详见 SQL DDL 注释）：
- 计划：`idx_plan_prov_op(provenance_code, operation_code)`、`idx_plan_expr(expr_proto_hash)`
- 切片：`idx_slice_prov_status(provenance_code, status_code)` + 唯一 `uk_slice_sig(plan_id, slice_signature_hash)`
- 任务：`idx_task_src_op(provenance_code, operation_code, status_code)`、`idx_task_queue(status_code, leased_until, priority, scheduled_at, id)`
- 运行：`idx_run_prov_op_status(provenance_code, operation_code, status_code)`
- 批次：`idx_batch_prov_op_status(provenance_code, operation_code, status_code, committed_at)`
- 游标：`uk_cursor_ns(provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)` 及时间/数值排序索引

设计影响：
- 查询 Dashboard / 监控无需再通过 task → slice → plan 回溯来源，只依赖各层冗余字段。
- 幂等键逻辑不变；冗余不参与幂等哈希，避免来源 code 变化导致历史幂等签名波动（来源 code 属稳定键）。
- 写入路径需在派生阶段填充 provenance_code（不可为 NULL），保持 lineage 完整性。

### 4.3 plan_key 用途
`plan_key` 作为对外（或调度层）可读 / 幂等识别键：
- 允许外部重复触发同一窗口/策略时复用已存在 plan（可选逻辑）。
- 与内部自增 ID 分离，便于日志与告警引用。

## 5. 概念要点
### 5.1 Schedule Instance
单次外部或内部调度触发的根；固化触发参数 + 来源/表达式原型快照摘要；Plan lineage anchor。
### 5.2 Plan
描述“总窗口 + 切片策略（时间/页/ID/预算）”，不执行；含表达式原型 proto snapshot/hash；与 Strategy 编译器耦合最小化。
### 5.3 Plan Slice
并行与幂等最小单元；承载 localized expr（注入 slice 的时间边界/起始分页/ID 范围）；`slice_signature_hash` 唯一化。
### 5.4 Task
一片一任务；包含 credential/operation/normalized params；幂等键避免重复派生；租约字段支撑多执行器竞争。
### 5.5 Task Run
一次尝试（首次/重试/回放），持有 checkpoint 与累积 stats，不覆盖 task。
### 5.6 Task Run Batch
分页 / token / 时间推进最小账目；写入顺序：插入记录 → 解析响应 → 去重/入库/事件 → 生成下一 hint → stats；幂等键保障重放。
### 5.7 Cursor Event / Cursor
事件 append-only，记录 lineage + prev/new；cursor 仅保存最新现值并仅前进；事件先行使得即便并发写现值失败仍具可回放性。

## 6. 快照结构：Proto vs Localized
| 维度 | Plan Proto (expr_proto_snapshot) | Slice Localized (expr_snapshot) |
|------|----------------------------------|---------------------------------|
| 窗口 | 总窗口（from/to） | 注入 slice 边界 (from_s,to_s) |
| 分页初值 | 无或全局初始描述 | 具体 offset/page/token 初值 |
| ID 范围 | 可能为空 | 具体 ID 段 / 哈希桶 |
| 表达式 | 通用模板（含变量占位） | 已绑定 slice 窗口/参数的具体模板 |
| 哈希 | expr_proto_hash | expr_hash |
| 复用判定 | spec_fingerprint + expr_proto_hash | slice_signature_hash + expr_hash |

## 7. 幂等与层次（概览）
| 层 | 唯一/幂等键 | 作用 | 失败/重放行为 |
|----|-------------|------|--------------|
| Slice | uk_slice_sig(plan_id, slice_signature_hash) | 防重复切片 | 已存在直接跳过生成 |
| Task | idempotent_key | 防重复派生 | 命中读取旧任务不再新建 |
| Run | uk_run_attempt(task_id, attempt_no) | 明确尝试序号 | 重试 attempt_no+1 |
| Batch | (run_id,batch_no) + before_token + idempotent_key | 批次去重 | 重放读旧 stats 继续推进 |
| Cursor Event | idempotent_key | 推进事件唯一 | 重复写忽略副作用 |
| Cursor | uk_cursor_ns(...) + 仅前进 | 防回退 | CAS 失败重读 |

## 8. 与 Registry 契约
Registry 表族 (`reg_prov_*`, 字典 `sys_dict_*`) 通过 **Spec Compiler** 编译为 Spec Snapshot：鉴权/分页/时间窗/限流/重试/表达式能力/映射。编译规则、策略位校验、降级路径详见：`../strategy/Ingest-strategy-schema-design.md` 与 reference 文件。Core 仅关心：
- 哪些字段写入 plan: expr_proto_hash / expr_proto_snapshot / spec_fingerprint
- 哪些字段写入 slice: expr_hash / expr_snapshot / slice_spec
- 失败条件：必填缺失 / 冲突 / 字典非法（直接终止 plan）

## 9. 验收 (Core DoD)
1. 任意对象可由 lineage (schedule→plan→slice→task→run→batch) 追溯。
2. 重放指定 run/batch 可还原请求参数上下文（不依赖 Registry 实时配置）。
3. Window 拼接不重不漏（半开区间统一）。
4. 幂等键命中行为明确：不重复写入而是读取旧状态继续。
5. Planner 与 Executor 部署独立，对运行中对象无破坏性影响。

---
后续：在 Guide 中补端到端示例；在 Reference 中列字段细节；Ops 说明计划失败排障标准。
