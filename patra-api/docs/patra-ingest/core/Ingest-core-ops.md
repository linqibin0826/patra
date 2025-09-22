# Ingest · Core Ops

导航：Core Schema | Guide | Reference | Core[本页] | Examples ｜ 返回：`../README.md`

关注规划阶段运维：计划失败诊断、切片长尾、库存与预算背压，不含执行期速率/错误自愈（见 runtime-ops）。

## 场景纲要
- 计划编译失败分类（字典缺失/必填字段/两段式不成对）
- 切片过粗 / 页深过大 早期识别指标
- 库存阈值 PARTIAL 状态处置
- 计划再切片触发请求查看

## 1. 编译失败分类 (Compile Failure Taxonomy)
| 分类 | 示例 | 处理策略 | 升级条件 |
|------|------|----------|----------|
| SYNTAX | 语法错误/未闭合括号 | 直接 FAIL plan | 永不重试 |
| UNSUPPORTED_FEATURE | 来源不支持 changedSince | 降级策略 (strategy-reference §3) | 3 次同类后标记降级 |
| VALIDATION_GATE | 窗口跨度>最大限制 | 调整窗口或 BACKFILL 分段 | 需求确认 |
| BUDGET_OVERFLOW | 预估记录数 > 预算上限 | 拆分/再切片 | 预算调优批准 |
| AMBIGUOUS_PAGINATION | 无法确定 OFFSET/TOKEN | 默认 TOKEN→OFFSET fallback | 二级审阅 |

## 2. 切片粒度诊断
| 指标 | 期望范围 | 过粗信号 | 过细信号 | 调整动作 |
|------|----------|----------|----------|----------|
| slice_avg_tasks | 50~500 | >2000 | <20 | 改进 slice_strategy_params |
| slice_fail_rate | <1% | >5% | - | 降低跨度或预算 |
| slice_skew_ratio (max/min window span) | <3 | >5 | ~1 且很小 | 动态再切片 or 合并 |
| reslice_trigger_interval | >30m | <5m 连续 | - | 检查表达式/来源抖动 |

## 3. PARTIAL 状态处理
| 场景 | 触发 | 动作 | 备注 |
|------|------|------|------|
| PLAN→PARTIAL | 某些切片失败 | 标记失败切片, 允许成功切片推进 | 不阻塞总体进展 |
| SLICE→PARTIAL | 批次部分成功 | 继续剩余任务派生 | 失败任务重入队 |
| 清理策略 | 所有失败 <5% 且 L1 | 自动重试成功后升级 COMPLETED | SLA 保障 |
| 升级失败 | 失败≥5% 或 L2+ | 人工介入 / 再切片 | 生成诊断包 |

## 4. 再切片 (Reslice) 触发判定
| 触发器 | 公式/条件 | 防抖 | 行为 |
|---------|----------|------|------|
| GRANULARITY_OVERFLOW | slice_avg_tasks > 阈值 | 15m 窗口 | 缩小窗口或拆分维度 |
| HIGH_SKEW | skew_ratio>5 | 2 次连续 | 对最大窗口切片再细分 |
| BUDGET_EXCEEDED | 估算>预算上限 | 即时 | 切片拆分 + 降级策略评估 |
| HIGH_RETRY | slice_fail_rate>10% 且集中单 slice | 10m | 单 slice 再切片 |

## 5. Reslice Runbook
1. 获取问题 plan_id。
2. 查询失败或超大切片: `SELECT slice_no,slice_spec,status_code FROM ing_plan_slice WHERE plan_id=?`。
3. 计算 skew_ratio 与 avg_tasks (参见 §2 指标)。
4. 选择策略:
	- 仅单一热点 slice: 生成子窗口切片。
	- 全局过粗: 重编译 plan（保留原表达式 snapshot，增加 finer 参数）。
5. 记录 reslice_reason (GRANULARITY_OVERFLOW/HIGH_SKEW/...) 写入审计。
6. 标记旧失败切片为 SUPERSEDED，插入新切片。
7. 验证新切片数量与期望范围。
8. 观察 1~2 个执行周期的 fail_rate 和 avg_tasks。

## 6. 诊断 SQL 片段
```
-- skew ratio
SELECT MAX(JSON_EXTRACT(slice_spec,'$.span'))/MIN(JSON_EXTRACT(slice_spec,'$.span')) skew
FROM ing_plan_slice WHERE plan_id=? AND status_code IN ('PENDING','EXECUTING');

-- fail distribution
SELECT slice_no,COUNT(*) fail_batches
FROM ing_task_run_batch WHERE plan_id=? AND status_code='FAILED'
GROUP BY slice_no ORDER BY fail_batches DESC LIMIT 10;
```

## 7. 常见决策矩阵
| 现象 | 根因候选 | 首选动作 | 备选 |
|------|----------|----------|------|
| 大量 PARTIAL | 微抖动 / API 限频 | AIMD 降速 + 重试 | Reslice |
| 单 slice 高失败 | 数据倾斜 | Reslice 该 slice | 降级分页模式 |
| 平均任务过大 | 初始窗口过宽 | 全局再切片 | BACKFILL 拆段 |

## 8. Checklist
- [x] 失败分类
- [x] 粒度诊断指标
- [x] PARTIAL 处理策略
- [x] 再切片触发与 Runbook
- [x] 决策矩阵 & SQL 片段
