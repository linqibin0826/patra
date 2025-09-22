# Ingest · Runtime Schema 设计索引

导航：Runtime[本页] | Guide | Reference | Ops | Observability ｜ 总览：`../README.md`

## 1. 双流水线概览
```
PlannerTrigger ──▶ schedule_instance ──▶ plan ──▶ slice ──▶ (derive) task(QUEUED)
ExecutorTrigger ──▶ dequeue task(lease) ─▶ run ─▶ batch loop ─▶ cursor_event ─▶ cursor
```
核心解耦：Planner 只做“确定性生成 + 库存控制 + 快照编译”，Executor 只对已派生 Task 做幂等消费与游标推进。二者通过数据库 + 幂等键连接。

## 2. 状态机总览 (摘要)
| 对象 | 主要状态 | 迁移触发 | 终止条件 |
|------|---------|---------|---------|
| plan | DRAFT→SLICING→READY/PARTIAL/FAILED | 编译/切片结果 | READY/PARTIAL/FAILED |
| slice | PENDING→READY/FAILED | 切片计算 | READY/FAILED |
| task | QUEUED→DISPATCHED→EXECUTING→SUCCEEDED/FAILED/PARTIAL/CANCELLED | 租约/执行结果 | 终态 |
| run | PLANNED→RUNNING→SUCCEEDED/FAILED/PARTIAL/CANCELLED | 尝试开始/批次结果 | 终态 |
| batch | RUNNING→SUCCEEDED/FAILED/SKIPPED | 请求/解析 | 终态 |

PARTIAL 语义：已取得部分成功但剩余窗口/预算需再切或后续任务补完，不视为 FAILED；需要 Planner/Strategy 判定是否触发在线再切片。

## 3. 任务租约模型
| 字段 | 含义 | 行为 |
|------|------|------|
| lease_owner | 当前持有者标识（实例 / workerId） | 抢占成功写入；用于诊断 |
| leased_until | UTC 到期时间 | 出队 WHERE 中判断是否可重新抢占 |
| lease_count | 抢占 + 续租累计次数 | 高值可能预示长尾/卡死 |

抢占流程：SELECT(QUEUED) → 尝试 UPDATE set status=DISPATCHED, lease_owner, leased_until WHERE id=? AND status=QUEUED AND (leased_until IS NULL OR leased_until < NOW(6)); 成功=获得租约。续租：周期性更新 leased_until，并 lease_count+1。

## 4. Run & Batch 生命周期与黑匣子
| 步骤 | 动作 | 写入 | 幂等要点 |
|------|------|------|---------|
| 1 | 创建 run | ing_task_run(status=RUNNING) | uk_run_attempt 防重复 attempt |
| 2 | 获取令牌 | - | Rate Gate 决策记录在 batch.stats |
| 3 | 渲染请求 | batch 预插入 (RUNNING) | idempotent_key 预先计算 |
| 4 | 发送/解析 | 更新 batch: items/token/hasNext/stats | 命中 uk_run_before_tok 直接读旧 |
| 5 | 入库/事件 | 外部 Outbox 幂等 | 唯一键+updatedAt 覆盖 |
| 6 | 下一步位置 | batch.after_token/page_no+1 | - |
| 7 | 终止判定 | hasNext=false 或预算/阈值 | - |
| 8 | 游标推进 | cursor_event → cursor | 事件先行，仅前进 |

黑匣子最小集：run.stats + 每个 batch.stats + expr_hash + slice_signature_hash + lineage IDs + cursor_event 链。

## 5. 失败与重试挂钩点
| 层 | 可重试 | 不可重试 | 动作 |
|----|-------|---------|-----|
| HTTP 请求 | 网络/5xx/429 | 4xx 业务 | Retry + 降速 / 批失败 |
| Batch | 临时解析失败 | schema 破坏 | 标记 FAILED；run 可能 PARTIAL |
| Run | 可继续窗口 | 致命鉴权/签名 | 新 run attempt / 终止 |
| Task | 剩余窗口可再切 | 多次致命 | 标记 PARTIAL/FAILED |

## 6. 与 Strategy 协作
- Executor 获取批次前调用 Rate Gate (acquire)。
- 429/高延迟/页深统计写回 run.batch stats；满足阈值→写“reSliceSuggested”标志供 Planner 消费。
- 预算耗尽：task 标记 PARTIAL 并停止后续 run。

## 7. 与 Cursor 推进交叉点
推进时机：run 成功覆盖一个“确定窗口增量”后（通常在最后一批或中间批达到窗口上界）。规则：计算 new watermark → 写 event → CAS 更新现值；BACKFILL 与 UPDATE 在独立命名空间，不影响 HARVEST 前向水位。

## 8. 验收 (Runtime DoD)
1. 多 Executor 实例下无重复执行（租约与批次幂等联合保证）。
2. 任一批次重放不会新增副作用（读旧 stats 跳过）。
3. 429/5xx 峰值下降后执行速率自恢复 (AIMD)。
4. 游标推进严格事件先行且无回退；BACKFILL 不影响前向水位。
5. PARTIAL 条件与再切片信号可被上游 Planner 正确消费。

---
更多运行中错误分级与自愈矩阵见 runtime-ops；指标字段见 runtime-observability。
