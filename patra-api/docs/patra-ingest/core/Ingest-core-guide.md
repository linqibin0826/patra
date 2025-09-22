# Ingest · Core Guide（入门）

导航：Core Schema | Core[本页] | Reference | Ops | Examples ｜ 返回：`../README.md`

## 目标
端到端示例：一次外部触发如何生成 schedule → plan → slice → task，并为执行阶段准备稳定上下文。

## 内容纲要
- 触发输入与前置校验
- Spec Snapshot 编译摘要（详细见 Strategy Schema Design）
- 计划创建与窗口确定
- 自适应切片基本流程（详细策略在 Strategy Guide）
- Task 派生与幂等键生成
- 常见失败与降级

## 1. 触发输入与前置校验
输入：scheduler_code, trigger_type, provenance_code, expression_raw, operation_code(HARVEST/BACKFILL/UPDATE), window(from,to?), optional safetyLag。
校验顺序：
1) provenance 存在 → 2) operation 可用 → 3) expression 语法与 AST → 4) window 合法(from<to,UTC) → 5) safetyLag ≤ provider.maxLag → 6) 组合指纹是否已在近窗口内执行（防重复触发）。

## 2. 编译与 Spec Snapshot（概要）
结果产出：expr_proto_hash, window.total, slice.strategy(+params), pagination.strategy, rate/baseRps, retry.policy, guardrails。
失败降级：
- 不支持 slice.strategy → TIME(default step)
- TOKEN 无 nextTokenPath → 降级 OFFSET
- 超大 pageSize → clamp provider.maxPageSize

## 3. Plan 创建与窗口确定
1) 计算 effective_to = min(window.to, now - safetyLag)
2) 记录 plan_key（可读），expr_proto_snapshot（原型），状态=DRAFT
3) 进入切片阶段：status→SLICING

## 4. 自适应切片流程（TIME 示例）
伪代码：
```
step = params.stepMinutes
cursor = window.from
while cursor < effective_to:
	next = min(cursor + step, effective_to)
	build slice_spec = {type:TIME, from:cursor, to:next}
	localize expression (add time range filter)
	persist slice (PENDING)
	cursor = next
```
失败策略：本地化失败 → 标记该 slice FAILED；统计后决策 plan READY / PARTIAL / FAILED。

## 5. Task 派生与幂等
公式：task.idempotent_key = SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))
去重：UNIQUE 冲突代表已派生；重复触发同参数不会新增任务。
调度准备：status=QUEUED，priority 默认=5，scheduled_at 可空代表立即。

## 6. 常见失败与降级决策表
| 场景 | 触发 | 降级/动作 | 指标 | 后续 |
|------|------|-----------|------|------|
| slice.strategy 不支持 | endpoint 能力缺失 | TIME+默认步长 | strategy.degrade.count | 记录备注 |
| TOKEN 分页缺路径 | nextTokenPath 空 | OFFSET | pagination.degrade.count | 预警 |
| pageSize 超限 | >maxPageSize | clamp | pagination.clamp.count | 告知调用方 |
| window 逆序 | from>=to | 拒绝触发 | plan.validation.fail.count | 人工修正 |
| safetyLag 超限 | >provider.maxLag | clamp | window.clamp.count | 调整配置 |
| 过多 slice 失败 | 失败率>阈值 | plan=PARTIAL | plan.partial.update.count | 后续再切片评估 |

## 7. 快速校验 Checklist
- [ ] provenance/operation 验证通过
- [ ] 表达式编译成功 (expr_proto_hash)
- [ ] window 与 safetyLag 合法并裁剪
- [ ] 切片结果 ≥1 且粒度未超上限
- [ ] 降级动作（如有）记录在 record_remarks
- [ ] plan 状态为 READY 或 PARTIAL（非空）
- [ ] 任务队列有对应 slice 数量的任务

## 8. 端到端时序（概要）
调度触发 → 校验/编译 → 保存 schedule_instance → 创建 plan(DRAFT) → 切片(SLICING) → 本地化 & 生成 slices(PENDING) → 汇总 plan 状态(READY/PARTIAL/FAILED) → 派生 tasks(QUEUED) → 交由执行器拉取。

## 9. 设计意图回顾
通过 Snapshot + 去重幂等键，确保：重复触发不放大执行；所有运行期行为（速率/分页/窗口裁剪）由稳定快照驱动，避免“读配置抖动”。

## 10. 下一步阅读指引
Strategy Guide：查看编译/降级细节与矩阵。
Runtime Reference：了解任务/运行/批次状态机。
Cursor Reference：理解推进与回放机制。

