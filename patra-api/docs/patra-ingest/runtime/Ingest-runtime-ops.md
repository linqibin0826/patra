# Ingest · Runtime Ops

导航：Runtime Schema | Guide | Reference | Runtime[本页] | Observability ｜ 返回：`../README.md`

错误分级 L1–L4、自愈动作矩阵、在线再切片协作、回放与隔离池处理。

## 纲要
- 错误分级表 (L1 可重试 / L2 不可重试 / L3 数据污染 / L4 致命)
- 自愈动作：降速 / 再切片 / 暂停下发 / 熔断
- 回放步骤：run_id + batch_no + expr_hash
- 隔离池与 UPDATE-Signals 回流
- 告警阈值初始建议

## 0. 初始化与巡检（迁移自根级 ingest-ops）
- 初始化数据库：执行 `sql/patra-ingest.sql`（确保 `provenance_config_snapshot` 与 `provenance_config_hash` 字段已存在于 `ing_plan`）。
- 字典对齐：Registry 必须提前写入 ingest 相关字典项 (`ing_scheduler`, `ing_operation`, `ing_slice_strategy`, `ing_task_status`, 等)。
- 结构巡检脚本（示例）:
  - 检查唯一键：`SHOW INDEX FROM ing_task WHERE Key_name='uk_task_idem';`
  - 检查新增指纹索引：`SHOW INDEX FROM ing_plan WHERE Key_name='idx_plan_fingerprint';`
  - 检查冗余来源列是否存在：`DESC ing_task` 中应含 `provenance_code`。
- 差异升级（若旧环境无 provenance_config_*）：
```sql
ALTER TABLE ing_plan ADD COLUMN `provenance_config_snapshot` JSON NULL COMMENT '来源配置/窗口/限流/重试等快照（中立模型）' AFTER expr_proto_snapshot;
ALTER TABLE ing_plan ADD COLUMN `provenance_config_hash` CHAR(64) NULL COMMENT '规范化来源配置哈希' AFTER provenance_config_snapshot;
CREATE INDEX idx_plan_prov_config_hash ON ing_plan(provenance_config_hash);
```

## 1. 错误分级矩阵
| 级别 | 定义 | 示例 | 是否自动重试 | 是否计入失败率 | 自愈/降级动作 | 升级条件 |
|------|------|------|--------------|----------------|--------------|----------|
| L1 Transient | 短暂性，可期望快速恢复 | 429, 502, connect timeout, read timeout, socket reset | 是（指数退避 ≤ maxAttempts 3-5） | 否（单独统计） | AIMD 减速 / backoff / 切换备用 credential | 连续窗口内错误率 > X% 升级 L2 |
| L2 Business | 业务确定性失败 | 参数非法, unsupported filter, auth invalid(非暂时), 400, 404 | 否 | 是 | 标记 batch FAILED；可触发 slice PARTIAL | 同类重复出现 影响≥N slice 升级 L3 风险审查 |
| L3 Data Pollution | 返回内容结构异常或污染风险 | JSON schema mismatch, required字段缺失, 大批量重复/空值异常 | 否（隔离） | 是 | 批次隔离→隔离池；下游不落主库；需人工审核 | 同数据污染影响窗口跨越 >T 分钟 升级 L4 停止 |
| L4 Fatal | 系统级或策略编排致命 | 表结构丢失、关键配置缺失、内部不一致、循环回放 | 否 | 是 | 立即熔断 plan / slice / task 链路 | 人工解除后方可恢复 |

## 2. 自愈策略决策树
| 条件 | 动作 | 细节 | 指标反馈 |
|------|------|------|----------|
| 连续 L1 同类型 ≥ 3 次 | 减速 | RPS = max(RPS * dec, minRps) | rate.decrease.count |
| L1 总量占比 > 30% 且窗口<5m | AIMD 半衰 | baseRps = baseRps * 0.5 | rate.halving.count |
| plan 下 slice L2 比例 > 20% | 标记 PARTIAL | plan.status=PARTIAL | plan.partial.update.count |
| 单 slice L2 重复 > 5 | 终止 slice | slice.status=FAILED | slice.fail.final.count |
| 检测数据污染(L3) | 隔离批次 | batch.status=FAILED + move to isolation store | batch.isolation.count |
| L3 连续窗口>15m | 停止计划 | plan.status=CANCELLED | plan.cancel.pollution.count |
| L4 触发 | 全链路熔断 | 停止调度新 task | runtime.fuse.trigger.count |

## 3. 指标 & 告警初值
| 指标 | 建议阈值 | 告警级别 | 说明 |
|------|----------|----------|------|
| rate.decrease.count | > 5 / 10m | WARN | 频繁减速需检查上游健康 |
| plan.partial.update.count | > 1 / plan | INFO | 早期发现配置问题 |
| batch.isolation.count | > 0 | WARN | 出现数据污染需审查 |
| cursor.lag.seconds | > 2 * safetyLag | WARN | 滞后扩张 |
| cursor.lag.seconds | > 4 * safetyLag | CRITICAL | 严重堵塞 |
| transient.error.ratio | > 0.5 (5m) | WARN | 上游不稳定 |
| business.error.ratio | > 0.2 (5m) | WARN | 规格/参数问题 |
| pollution.suspect.ratio | > 0.05 (10m) | WARN | 结构异常增长 |
| fatal.count | ≥1 | CRITICAL | 需人工处理 |

## 4. 回放流程 (Task Run Batch)
步骤：
1. 定位 `run_id` 与异常 `batch_no`
2. 读取 batch before_token / page_no / window 边界
3. 重建执行上下文（expr_snapshot + credential + rate 限制）
4. 以只写隔离池模式重新抓取 → 验证记录 count / schema
5. 若通过签名校验（数据与预期一致）→ 正式写入主库并补写 cursor_event（必要时）
6. 记录回放事件 (audit) 与 diff（如有）

## 5. 隔离池策略
| 项目 | 策略 |
|------|------|
| 存储 | 单独库/表 或 加标签字段 `isolation=1` |
| 写入触发 | L3 或未知结构异常 |
| 审核通过迁移 | 批量 upsert -> 主数据域 |
| 审核拒绝 | 归档 + 丢弃理由记录 |
| 追踪 | lineage + expr_hash + batch_no |

## 6. 在线再切片 (动态调整)
触发：
- Slice 执行时间 > SLA (如 > 30m)
- 连续多批 L1 导致推进缓慢
- 观测窗口内 record_count 偏离均值 > 3σ

动作：
1. 标记原 slice 状态为 PARTIAL (若已产出数据) 或 CANCELLED
2. 按剩余窗口/ID 区间重新切片（更细 granularity）
3. 新 slice 挂载同 plan，生成新 task 队列
4. 原未完成 task 取消租约

## 7. 速率自适应 (AIMD) 执行细节
| 事件 | 调整 | 公式 |
|------|------|------|
| 成功批次 | 加性增加 | r = min(r + inc, maxRps) |
| L1 错误 | 乘性减 | r = max(r * (1 - dec%), minRps) |
| 进入降级 | 固定下调 | r = floor(r * 0.5) |
| 恢复稳定（5 连续成功窗口） | 恢复基线 | r = min(baseRps, currentRps) |

## 8. 关键操作 Runbook
| 场景 | 诊断命令/SQL | 期望输出 |
|------|--------------|----------|
| 租约僵死清理 | SELECT id FROM ing_task WHERE status_code='RUNNING' AND leased_until<NOW(6); | 列出超时任务 |
| 回放单批 | SELECT * FROM ing_task_run_batch WHERE run_id=? AND batch_no=?; | 获取原批次上下文 |
| 统计错误分布 | 聚合 run.stats->$.failed / upserted | 错误比率 |
| 滞后追踪 | 查询 cursor.lag.seconds 指标 | 当前滞后 |

## 9. 联动 Cursor 策略
当大量 L1 导致推进缓慢：
1. 计算 lag = observed_max - current
2. lag 超阈值且仍高 L1 比例 → 减速 + 触发 BACKFILL 准备
3. 若 L2/L3 增长 → 暂停 cursor 前进（仅写事件）避免推进污染

## 10. DoD (Ops)
1. 错误分级清晰且示例覆盖常见来源。
2. 自愈动作可由配置（阈值/比率）驱动。
3. 回放与隔离池流程明确并可自动化。
4. 再切片触发条件与动作原子化。
5. 指标阈值具备初始默认值，可运维调优。
6. 与 Cursor 滞后联动策略可执行。
