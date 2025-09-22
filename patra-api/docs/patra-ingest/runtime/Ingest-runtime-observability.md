# Ingest · Runtime Observability

导航：Runtime Schema | Guide | Reference | Ops | Runtime[本页] ｜ 返回：`../README.md`

黑匣子与指标：批次级最小观测集、速率与配额、错误/重试、晚到率、再切片触发、游标推进。

## 1. 指标分层总览
| 层 | 目标 | 核心关注 |
|----|------|----------|
| Planner | 编排效率 | 切片速度/数量/失败比例 |
| Executor | 执行吞吐 | 排队延迟/批次延迟/错误比分布 |
| Rate Gate | 速率自适应 | 当前RPS/加减事件/等待时长 |
| Cursor | 推进健康 | 推进间隔/滞后秒数/无前进事件 |
| Data | 数据质量 | upsert 成功率/去重率/晚到率 |
| Reliability | 稳定性 | 重试、熔断、部分失败趋势 |

## 2. Metrics 规范
命名：`ingest.<domain>.<metric>`；标签(label) 包含：`provenance`,`operation`,`endpoint`（若适用）；必要时添加 `cursor_key`、`namespace_scope`。控制基数。 
类型：Counter(累计事件) / Gauge(瞬时) / Histogram(分布)。比率通过查询层聚合，不直接暴露大量派生指标。

## 3. Metrics 明细
| 名称 | 类型 | 标签 | 描述 | 采集点 |
|------|------|------|------|------|
| ingest.planner.compile.ms | Histogram | provenance,operation | 表达式+策略合成耗时 | plan 完成 |
| ingest.planner.slice.count | Counter | provenance,operation | 生成切片数 | 切片完成 |
| ingest.planner.slice.failed.count | Counter | provenance,operation | 切片失败数 | 切片完成 |
| ingest.planner.slice.partial.count | Counter | provenance,operation | 部分失败切片数 | 汇总 |
| ingest.executor.task.queue.wait.ms | Histogram | provenance,operation | 任务排队等待时长 | 任务启动 |
| ingest.executor.batch.latency.ms | Histogram | provenance,operation,endpoint | 外部请求+处理耗时 | 批次完成 |
| ingest.executor.batch.size | Histogram | provenance,operation | 批次记录数 | 批次完成 |
| ingest.executor.retry.count | Counter | provenance,operation | L1 重试次数 | 重试触发 |
| ingest.executor.transient.error.count | Counter | provenance,operation | L1 错误次数 | 错误分类 |
| ingest.executor.business.error.count | Counter | provenance,operation | L2 错误次数 | 错误分类 |
| ingest.executor.pollution.count | Counter | provenance,operation | L3 数据污染事件 | 隔离时 |
| ingest.executor.fatal.count | Counter | provenance,operation | L4 致命事件 | 熔断 |
| ingest.rate.current.rps | Gauge | provenance,operation | 当前 RPS | 周期/变更 |
| ingest.rate.adjust.down.count | Counter | provenance,operation | AIMD 下调次数 | 调整 |
| ingest.rate.adjust.up.count | Counter | provenance,operation | AIMD 上调次数 | 调整 |
| ingest.rate.wait.ms | Histogram | provenance,operation | 速率门控等待时长 | 请求前 |
| ingest.cursor.advance.count | Counter | provenance,operation,cursor_key | 推进事件 | cursor_event 插入 |
| ingest.cursor.no_forward.count | Counter | provenance,operation,cursor_key | 候选未前进 | 事件写入 |
| ingest.cursor.lag.seconds | Gauge | provenance,operation,cursor_key | 滞后秒数 | 周期计算 |
| ingest.cursor.stagnation.count | Counter | provenance,operation,cursor_key | 推进停滞告警次数 | 检测器 |
| ingest.data.upsert.count | Counter | provenance,operation | 成功 upsert 记录 | 批次提交 |
| ingest.data.skip.count | Counter | provenance,operation | 去重/过滤跳过 | 批次提交 |
| ingest.data.late.count | Counter | provenance,operation | 晚到记录 | 批次判定 |
| ingest.data.window.coverage.ratio | Gauge | provenance,operation | 窗口覆盖比例 | 周期计算 |
| ingest.reliability.slice.reslice.count | Counter | provenance,operation | 再切片次数 | 触发再切片 |
| ingest.reliability.backfill.trigger.count | Counter | provenance,operation | BACKFILL 触发次数 | 触发时 |

## 4. Dashboard 视图建议
| 看板 | 图表 | 说明 |
|------|------|------|
| Overview | 窗口覆盖率, 滞后秒数, 吞吐 | 推进+产出总览 |
| Rate & Errors | RPS vs 错误堆叠 | 判断降速/调参 |
| Cursor | lag 趋势 + no_forward 比例 | 识别停滞/抖动 |
| Data Quality | upsert/skip/late 比率 | 清洁度与晚到 |
| Reliability | 再切片/Backfill/熔断事件 | 自愈效果 |

## 5. 日志字段建议
| 字段 | 说明 |
|------|------|
| traceId | 跨服务关联 |
| planId,sliceId,taskId,runId,batchNo | 执行链路定位 |
| exprHash | 表达式版本追踪 |
| token,pageNo | 分页策略调试 |
| latencyMs,parseMs | 性能剖析 |
| errorClass,errorCode | 错误体系对齐 |
| dedupeCount,lateCount | 数据质量统计 |

## 6. Trace Spans
| Span | 标签 | 说明 |
|------|------|------|
| ingest.plan.compile | planId,exprHash | 编译策略合成 |
| ingest.slice.generate | sliceStrategy,sliceCount | 切片过程 |
| ingest.task.dequeue | taskId,priority | 出队租约 |
| ingest.run.attempt | runId,attempt | 单次执行尝试 |
| ingest.batch.fetch | batchNo,pageNo,token | 外部请求 |
| ingest.batch.persist | recordCount | 数据持久化 |
| ingest.cursor.advance | cursorKey,lagSec | 游标推进 |

## 7. 采集频率
| 指标类型 | 周期 | 说明 |
|----------|------|------|
| Gauge(lag,coverage) | 30s | 平衡实时/成本 |
| current.rps | 15s | 自适应反馈敏捷 |
| Histograms | 事件触发 | 完成时采集 |
| Derived Ratios | 60s | 查询层聚合 |

## 8. 告警示例
```
ingest_cursor_lag_seconds > (4 * safety_lag_seconds)
# Transient 错误爆发
tincrease(ingest_executor_transient_error_count[5m]) / max(increase(ingest_executor_batch_size_bucket[5m])) > 0.5
# 游标停滞 10 分钟
increase(ingest_cursor_advance_count[10m]) == 0
```

## 9. 成熟度路线图
| 阶段 | 能力 | 标志 |
|------|------|------|
| M0 | 基础计数 | 核心 Counter/Gauge 完整 |
| M1 | 分布延迟 | Histogram + P95 面板 |
| M2 | 自愈闭环 | 指标触发降速/再切片自动化 |
| M3 | 预测 | 滞后趋势预测 + Backfill 预热 |

## 10. 跨文档关联
| 主题 | 引用 |
|------|------|
| 滞后计算 | cursor-reference §7 |
| 再切片 | runtime-ops §6 |
| 错误分级 | runtime-ops §1 |
| stats 映射 | runtime-reference §4 |

## 11. DoD
1. 指标集合覆盖推进/性能/质量/稳定/自愈。
2. 命名/标签规则统一且控制基数。
3. Trace/日志字段支撑根因定位。
4. 告警示例可直接落地。
5. 成熟度路线图明确扩展方向。
