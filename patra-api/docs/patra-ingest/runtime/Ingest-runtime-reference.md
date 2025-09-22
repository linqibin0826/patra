# Ingest · Runtime Reference

导航：Runtime Schema | Guide | Runtime[本页] | Ops | Observability ｜ 总览：`../README.md`

## 1. 统一状态机
| 对象 | 状态集合 | 进入条件 | 终止/派生条件 | 不可逆约束 |
|------|----------|----------|---------------|------------|
| ing_plan | DRAFT → SLICING → READY / PARTIAL / FAILED / COMPLETED | 创建= DRAFT；开始切片 = SLICING | 所有 slice 生成成功=READY；部分失败=PARTIAL；全失败=FAILED；全部关联 task 完成=COMPLETED | 不回退到 DRAFT |
| ing_plan_slice | PENDING → DISPATCHED → EXECUTING → SUCCEEDED / FAILED / PARTIAL / CANCELLED | 切片生成=PENDING；任务派生=DISPATCHED；首次任务运行=EXECUTING | 全任务成功=SUCCEEDED；部分失败=PARTIAL；全失败=FAILED | 不回退到 PENDING |
| ing_task | QUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED | 任务创建=QUEUED；租约获取并启动=RUNNING | 正常完成=SUCCEEDED；达最大重试且失败=FAILED；人工/系统停止=CANCELLED | SUCCEEDED 不再变更 |
| ing_task_run | PLANNED → RUNNING → SUCCEEDED / FAILED / CANCELLED | 创建尝试=PLANNED；执行器启动=RUNNING | 全批次成功=SUCCEEDED；错误失败=FAILED；人工取消=CANCELLED | 不回退到 PLANNED |
| ing_task_run_batch | RUNNING → SUCCEEDED / FAILED / SKIPPED | 批次提交=RUNNING | 数据入库成功=SUCCEEDED；错误=FAILED；条件跳过=SKIPPED | 终态后不可修改 |

状态字段来源：对应表的 status_code。PARTIAL 仅对 plan_slice 与 plan / task_run (可选) 表示存在部分失败但可继续。

## 2. 状态转换规则
| 对象 | 允许转换 | 触发来源 | 侧写备注 |
|------|----------|----------|----------|
| plan | DRAFT→SLICING | 调度编排 | 生成切片同步/异步 |
| plan | SLICING→READY/PARTIAL/FAILED | 切片生成结果 | 汇总 slice 成功/失败计数 |
| plan | READY/PARTIAL→COMPLETED | 所有关联 task 终态 | 统计所有 task 状态集合 |
| slice | PENDING→DISPATCHED | 派生任务 | 记录派生时间 |
| slice | DISPATCHED→EXECUTING | 首个 task 进入 RUNNING | 并发时幂等判定 |
| slice | EXECUTING→SUCCEEDED/PARTIAL/FAILED/CANCELLED | 任务聚合 | 聚合下游 task_run 终态 |
| task | QUEUED→RUNNING | 租约获取 | CAS+更新 leased_until |
| task | RUNNING→SUCCEEDED/FAILED/CANCELLED | run 聚合 | run 尝试结果归并 |
| run | PLANNED→RUNNING | 执行器启动 | 心跳开始 |
| run | RUNNING→SUCCEEDED/FAILED/CANCELLED | 批次全部终态 | 统计批次结果/错误策略 |
| batch | RUNNING→SUCCEEDED/FAILED/SKIPPED | 批次执行 | 数据提交 + 异常分类 |

## 3. 核心字段速览
| 表 | 字段 | 作用 | 说明 |
|----|------|------|------|
| ing_task | lease_owner | 租约持有者 | 执行器实例标识 |
| ing_task | leased_until | 租约到期 | 过期可被重新抢占 |
| ing_task | lease_count | 抢占/续租计数 | 观察频繁续租风险 |
| ing_task | priority | 调度优先级 | 1 高 9 低 |
| ing_task | scheduled_at | 定时启动点 | 可为空=立即 |
| ing_task_run | attempt_no | 第几次尝试 | 重试递增 |
| ing_task_run | checkpoint | 断点 | nextToken/pageNo/自定义 |
| ing_task_run | stats | 聚合统计 | 与批次累加一致 |
| ing_task_run_batch | batch_no | 批次序号 | 1 起递增 |
| ing_task_run_batch | before_token/after_token | 分页游标 | TOKEN 模式关键 |
| ing_task_run_batch | idempotent_key | 幂等键 | run_id + before_token/page_no |
| ing_task_run_batch | record_count | 本批记录数 | 数据吞吐统计 |
| ing_task_run_batch | committed_at | 提交时间 | 用于速率/延迟计算 |

## 4. 统计字段建议 (stats JSON)
| 维度 | Key | 描述 |
|------|-----|------|
| 计数 | fetched | 原始返回记录数 |
| 计数 | upserted | 成功落库/更新数 |
| 计数 | skipped | 被过滤/去重数 |
| 计数 | failed | 本批失败记录数 |
| 分页 | pages | 已处理分页数 |
| 分页 | tokensUsed | token 步进次数 |
| 性能 | latencyMs | 单批远程耗时 |
| 性能 | parseMs | 解析耗时 |
| 错误 | transientErrors | 短暂错误计数 |
| 错误 | businessErrors | 业务判定错误计数 |
| 资源 | bytesIn | 原始字节数 |
| 资源 | bytesOut | 持久化后字节数 |

聚合：run.stats = Σ(batch.stats)（可在写入时增量归并，避免最终 scan）。

## 5. 队列索引与查询模式
| 目的 | 典型 SQL 条件 | 对应索引 |
|------|--------------|----------|
| 拉取待执行任务 | status='QUEUED' AND (leased_until IS NULL OR leased_until < NOW(6)) ORDER BY priority,scheduled_at,id LIMIT N | idx_task_queue |
| 统计某来源操作进度 | provenance_code=? AND operation_code=? AND status_code IN (...) | idx_task_src_op |
| 监控租约超时 | status='RUNNING' AND leased_until < NOW(6) | idx_task_queue |
| 查询任务运行历史 | task_id=? ORDER BY attempt_no | uk_run_attempt |
| 分析批次执行时序 | run_id=? ORDER BY batch_no | uk_run_batch_no |
| 定位失败批次 | run_id=? AND status_code='FAILED' | idx_batch_status_time |

## 6. 幂等与重试策略
| 层级 | 幂等键/条件 | 重试机制 | 防重入口 |
|------|-------------|----------|---------|
| Task | idempotent_key | 若调度重复构建相同 slice 不再新增 | UNIQUE 约束 + 捕获冲突 |
| Run | (task_id,attempt_no) | attempt_no 递增 | UNIQUE uk_run_attempt |
| Batch | idempotent_key | before_token/page_no 决定；失败重试新批次 | UNIQUE uk_batch_idem/uk_run_before_tok |

## 7. 时间线与 lineage 聚合
执行链路：schedule_instance → plan → slice → task → run → batch。
每层冗余 provenance_code / operation_code / expr_hash 以支撑直接过滤，不必反查上游。

## 8. 性能注意事项
| 场景 | 风险 | 缓解 |
|------|------|------|
| 深分页 OFFSET | 扫描大 | 优先 TOKEN；或 LIMIT+id>last_id 翻页 |
| 高频租约续租 | 热点写 | 指数退避续租频率；拆分 shard | 
| 大量事件插入 | IO 放大 | 批量 insert; 合理分区未来规划 |
| stats JSON 过大 | 行膨胀 | 仅保留聚合关键，原始明细流式到对象存储 |

## 9. 一致性检查 (Health Checklist)
| 检查 | 条件 | 告警指标 |
|------|------|---------|
| 任务僵死 | status='RUNNING' AND leased_until < NOW(6) AND heartbeat 超时 | task.zombie.count |
| 批次长时间未提交 | batch RUNNING > threshold | batch.stall.count |
| Run 无批次推进 | run RUNNING 且最近无新 batch_no | run.stall.count |
| Slice 永久 PENDING | slice PENDING 超过阈值 | slice.pending.timeout.count |
| Plan SLICING 过久 | plan SLICING 超过阈值 | plan.slicing.timeout.count |

## 10. 指标聚合建议
结合 Observability 文件：
- Throughput: Σ(batch.record_count)/时间
- Error Ratio: failed / (upserted+failed)
- Latency P95: 基于 batch.latencyMs 直方图
- Lease Churn: lease_count 增量 / 时间

## 11. 设计完成 DoD (Runtime Reference)
1. 所有状态与转换可映射到单一表格（上文 1/2 节）。
2. 每层幂等入口与冲突处理明确（第 6 节）。
3. stats 字段清单与聚合策略明确（第 4 节）。
4. 队列/检索 SQL 有索引支撑（第 5 节）。
5. 健康检查项列出监控指标（第 9 节）。
6. 性能风险与缓解策略收录（第 8 节）。
