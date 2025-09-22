# Ingest · Cursor Schema 设计索引

导航：Cursor[本页] | Guide | Reference | Ops ｜ 总览：`../README.md`

## 1. 设计目标与挑战
统一不同操作(HARVEST/BACKFILL/UPDATE) 与不同增量形态 (TIME/ID/TOKEN) 的进度推进；保证：
- 不回退（单调）
- 可审计（append-only 事件账）
- 可回放（事件序列足以重建现值）
- 多命名空间隔离（避免回填影响前向）

挑战：晚到/乱序、TOKEN 不可比较、并发推进冲突、水位回放性能。

## 2. 命名空间矩阵
| 操作 | namespace_scope | namespace_key | direction | cursor_type(主) | 说明 |
|------|-----------------|---------------|-----------|-----------------|------|
| HARVEST | EXPR / GLOBAL | (expr_hash 或端点逻辑) | FORWARD | TIME | 主前向水位 |
| BACKFILL | CUSTOM | plan_id / activity_hash | BACKFILL | TIME | 不影响前向 |
| UPDATE(刷新-陈旧) | CUSTOM | refresh_campaign_id | FORWARD | TIME | 刷新进度 |
| UPDATE(按ID信号) | CUSTOM | signal_batch_id | FORWARD | ID | ID 扫描位置 |

TOKEN 型通常只在 batch 级使用，不常作为长期 cursor_type；若使用需存 `cursor_key=token` 但无全局比较序。

## 3. 事件模型 vs 现值
| 表 | 作用 | 更新策略 | 重放作用 |
|----|------|----------|---------|
| ing_cursor_event | 不可变推进记录 | 仅插入 | 重建时间线/诊断/回放 |
| ing_cursor | 最新现值 | 事件后 CAS 仅前进 | 快速读取当前进度 |

事件包含 lineage (schedule/plan/slice/task/run/batch/expr_hash) + prev/new 值 + direction + 命名空间。

## 4. 推进算法（TIME/ID）
伪代码：
```
prev = loadCursor(ns)
candidate = deriveFromProcessedBatches()
if candidate <= prev.value: // 不前进
	writeEvent(prev, prev, reason="no-forward")
	return
writeEvent(prev, candidate)
CAS update cursor set value=candidate where value < candidate
```
TOKEN 场景：只记录最近 token（不可比较），主要用于长游标接口；若需恢复，按 batch.after_token 重跑后续批次。

## 5. 并发与 CAS
多个 Executor 可并行处理同命名空间：
- 事件总能插入（不同 idempotent_key）
- cursor 更新使用 version / 比较字段 CAS，失败后读取最新值确认是否仍需前进
- 不要求严格序列化，只要求不回退

## 6. 边界与晚到
窗口半开 `[from,to)`：等于 to 的记录归下一窗口。SafetyLag 减少晚到概率；仍晚到的记录在下一窗口覆盖（唯一键 + updatedAt）。不回退现值，避免抖动。

## 7. 回放能力
回放策略：
1. 读取事件序列按时间排序
2. 过滤目标命名空间与操作类型
3. 顺序应用 prev→new（忽略非前进重复）
4. 与当前 cursor 校对差异

若 cursor 丢失，可用事件重建；若事件存在缺口（序列化丢失），需人工校验数据完整性（不在本阶段自动处理）。

## 8. 验收 (Cursor DoD)
1. HARVEST 与 BACKFILL 并行不会改写前向 cursor 记录。
2. 事件序列可重建 cursor 现值，无额外外部状态依赖。
3. 无记录出现“回退更新”（仅前进保证）。
4. 晚到记录仅通过后续窗口覆盖，不触发水位倒退。
5. 回放开销 O(N_events_selected) 可控（事件按时间/命名空间索引）。

---
详细字段、幂等键、伪代码见 cursor-reference；运维巡检/回放手册见 cursor-ops。
