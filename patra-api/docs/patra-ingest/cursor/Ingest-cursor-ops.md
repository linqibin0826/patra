# Ingest · Cursor Ops

导航：Cursor Schema | Guide | Reference | Cursor[本页] ｜ 返回：`../README.md`

运维关注：水位停滞、命名空间冲突、回放、归档与压缩。

## 场景纲要
- 停滞检测：窗口推进阈值
- 回放：基于 event 序列重建现值
- 归档策略：事件保留期 / 采样压缩
- 命名空间冲突排查

## 1. 停滞 (Stagnation) 检测
| 条件 | 定义 | 动作 |
|------|------|------|
| forward_distance=0 且有成功批次 | 连续 M=5 周期 | 标记 STAGNANT，触发诊断 |
| lag_sec 持续上升 | 滞后 > 上次 *1.2 连续3周期 | 检查来源延迟/策略速率 |
| event_gap >1 | cursor_event.sequence 不连续 | 阻塞推进并告警 |

## 2. 诊断步骤
1. 查询最近事件: `SELECT * FROM ing_cursor_event WHERE cursor_namespace=? ORDER BY id DESC LIMIT 20`。
2. 检查是否存在 REPLAY/RESLICE 事件打断链。
3. 验证对应任务批次是否成功提交 (runtime-reference §1 状态机)。
4. 若批次成功但未 forward → 可能是安全滞后限制；评估安全滞后调整。

## 3. 回放 (Replay) Runbook
| 步骤 | 描述 |
|------|------|
| 1 | 计算回放窗口: from = cursor_value - safetyLag, to = cursor_value |
| 2 | 生成 REPLAY plan (BACKFILL namespace or CUSTOM) |
| 3 | 执行并监控回放切片成功率 |
| 4 | 回放完成后不推进主 cursor，仅在确认补偿数据落地后再强制 forward (type=FORCE_FORWARD) |

## 4. 强制前进 (Force Forward)
| 场景 | 检查 | 动作 |
|------|------|------|
| 来源不可逆，数据清洗后需跳过 | 最近失败窗口重复失败>阈值 | 写 FORCE_FORWARD 事件 (记录 reason) |
| 大量重复/空批次 | 连续空批次数≥N | 增加窗口跨度或调整分页 |

## 5. 保留与归档策略
| 对象 | 保留周期 | 归档形式 |
|------|----------|----------|
| cursor_event | 30 天在线 | 冷存储 JSON 压缩 |
| replay plan | 90 天 | 元数据 + 指纹 |
| force_forward 记录 | 永久 | 审计链 |

## 6. Namespace 冲突排查
| 现象 | 原因 | SQL 检查 | 解决 |
|------|------|----------|------|
| 计划误用 GLOBAL | 指定表达式需要独立增量 | 查 plan.operation 与 expr | 重建计划改 EXPR |
| BACKFILL 污染 lag | 使用 GLOBAL 回放 | 查 cursor_event reason | 改用 CUSTOM 回放 |
| EXPR 与 GLOBAL 跨越不一致 | 未统一安全滞后 | 对比 safety_lag | 调整策略重新编译 |

## 7. 指标监控建议
| 指标 | 阈值 | 行动 |
|------|------|------|
| cursor_forward_distance | 0 连续5周期 | 启动停滞诊断 |
| cursor_replay_count | >0 | 审核原因，防止过度 |
| cursor_force_forward_count | >0 | 审计合法性 |
| cursor_event_gap | >0 | 排查缺失事件 |

## 8. Checklist
- [x] 停滞检测
- [x] 回放 Runbook
- [x] 强制前进策略
- [x] 归档策略
- [x] Namespace 冲突排查
- [x] 指标建议
