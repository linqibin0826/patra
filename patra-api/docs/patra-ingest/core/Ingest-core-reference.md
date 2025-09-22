# Ingest · Core Reference


导航：Core Schema | Guide | Core[本页] | Ops | Examples ｜ 总览：`../README.md`

## 表与对象概览（指向 runtime / strategy / cursor 各自细节）
- ing_schedule_instance
- ing_plan
- ing_plan_slice
- ing_task (结构本身在 runtime-reference 详述状态机/租约列)
- ing_task_run / ing_task_run_batch（详见 runtime）

## Plan / Slice 字段（快照原型与局部化）
- expr_proto_hash / expr_proto_snapshot
- expr_hash / expr_snapshot
- slice_spec / slice_signature_hash

## 幂等层次概览
- Task: idempotent_key
- Slice: uk_slice_sig
- Plan: 通过下游幂等间接实现

## 1. ing_schedule_instance 关键字段
| 字段 | 说明 | 备注 |
|------|------|------|
| scheduler_code / job_id / log_id | 外部调度溯源 | 组合索引 idx_sched_src |
| provenance_code | 来源代码快照 | 编排聚合加速 |
| provenance_config_snapshot | 来源配置中立快照 | 不随运行时变动 |
| expr_proto_hash / snapshot | 原型表达式及 AST | 后续局部化基线 |
| triggered_at | 触发时间 | 统计入口 |

## 2. ing_plan 关键字段
| 字段 | 说明 | 备注 |
|------|------|------|
| plan_key | 人类可读/幂等键 | uk_plan_key |
| operation_code | 操作类型 | HARVEST/BACKFILL/UPDATE |
| window_from/to | 总窗口 | 半开区间 `[from,to)` |
| slice_strategy_code / slice_params | 切片策略 | TIME/ID_RANGE/... |
| expr_proto_hash/snapshot | 原型保留 | 对比 diff |
| spec_fingerprint | Spec Snapshot 指纹 | 快照复用/回放一致性 |
| status_code | 计划状态 | READY/PARTIAL/FAILED/COMPLETED |

## 3. ing_plan_slice 关键字段
| 字段 | 说明 | 备注 |
|------|------|------|
| slice_no | 序号 | 唯一 (plan_id, slice_no) |
| slice_signature_hash | 通用边界签名 | 去重/幂等 |
| slice_spec | JSON 边界 | 时间/ID/token/预算 |
| expr_hash / snapshot | 局部化表达式 | 增量调试 |
| status_code | 状态 | PENDING/EXECUTING/SUCCEEDED/FAILED/PARTIAL |

## 4. 关联与冗余
| 目的 | 冗余列 | 价值 |
|------|--------|------|
| 快速过滤来源+操作 | provenance_code, operation_code (计划/切片/任务) | 降低多表 join |
| 表达式变更溯源 | expr_proto_hash / expr_hash | 回放与审计 |
| 性能 | slice_signature_hash | 避免重复构造 |

## 5. 幂等层次
| 层级 | 机制 | 冲突行为 | 说明 |
|------|------|----------|------|
| Slice | uk_slice_sig(plan_id,signature) | 忽略重复 | 同一边界不重复生成 |
| Task | idempotent_key | 返回已存在 | 防重复触发派生 |
| Batch | idempotent_key | 跳过插入 | 断点续跑保障 |
| Cursor Event | idempotent_key | 忽略重复 | 事件幂等审计 |

## 6. 索引摘要（Core 部分）
| 表 | 索引 | 目的 |
|----|------|------|
| ing_schedule_instance | idx_sched_src | 按外部 job/log 聚合 |
| ing_plan | idx_plan_prov_op | 来源+操作过滤 |
| ing_plan_slice | uk_slice_sig | 去重幂等 |
| ing_plan_slice | idx_slice_status | 进度监控 |

## 7. 典型查询
| 需求 | 条件片段 | 备注 |
|------|----------|------|
| 查看计划进度 | SELECT status_code,count(*) FROM ing_plan_slice WHERE plan_id=? GROUP BY status_code | 百分比计算 |
| 重建本地化差异 | SELECT slice_no, expr_hash FROM ing_plan_slice WHERE plan_id=? | 对比变更 |
| 切片失败排查 | SELECT * FROM ing_plan_slice WHERE plan_id=? AND status_code='FAILED' LIMIT 50 | 快速调试 |

## 8. 与其他文档的映射
| 概念 | 详述位置 |
|------|----------|
| 切片策略分类 | strategy-schema-design §2 |
| 运行状态机 | runtime-reference §1 |
| 游标推进事件 | cursor-schema-design §4+ |

## 9. Checklist
- [x] 关键字段分层
- [x] 幂等层次列出
- [x] 索引/查询指引
- [x] 跨文档映射

## 10. 字典类型依赖（与 Registry 对齐）
> 统一列出 Ingest 侧使用的字典 code 类型，原先位于根目录 `ingest-reference.md`，现迁移于此，保持单点权威。字典取值与描述参见 `docs/patra-registry/dict/*` 与 Registry DDL。

| 字典类型 | 说明 | 典型取值示例 |
|----------|------|--------------|
| ing_scheduler | 外部调度器来源 | XXL / QUARTZ / INTERNAL |
| ing_trigger_type | 触发类型 | SCHEDULE / MANUAL / RETRY |
| ing_operation | 采集操作类型 | HARVEST / BACKFILL / UPDATE / METRICS |
| ing_slice_strategy | 切片策略 | TIME / ID_RANGE / CURSOR_LANDMARK / VOLUME_BUDGET / HYBRID |
| ing_plan_status | 计划状态 | DRAFT / SLICING / READY / PARTIAL / FAILED / COMPLETED |
| ing_slice_status | 切片状态 | PENDING / DISPATCHED / EXECUTING / SUCCEEDED / FAILED / PARTIAL / CANCELLED |
| ing_task_status | 任务状态 | QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED |
| ing_task_run_status | 运行尝试状态 | PLANNED / RUNNING / SUCCEEDED / FAILED / CANCELLED |
| ing_batch_status | 批次状态 | RUNNING / SUCCEEDED / FAILED / SKIPPED |
| ing_namespace_scope | 游标命名空间范围 | GLOBAL / EXPR / CUSTOM |
| ing_cursor_type | 游标类型 | TIME / ID / TOKEN |
| ing_cursor_direction | 推进方向 | FORWARD / BACKFILL |

> 补充：若未来新增字典（例如 `ing_retry_policy`、`ing_rate_alg`），应在此处登记并链接对应 strategy 文档章节。
