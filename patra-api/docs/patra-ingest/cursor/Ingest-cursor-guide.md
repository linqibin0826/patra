# Ingest · Cursor Guide

导航：Cursor Schema | Cursor[本页] | Reference | Ops ｜ 返回：`../README.md`

如何在三类操作中使用游标：HARVEST 前向推进、BACKFILL 独立命名空间、UPDATE 刷新指针。

## 纲要
- HARVEST: FORWARD + (EXPR/GLOBAL) 示例
- BACKFILL: BACKFILL + CUSTOM namespace，禁止回退
- UPDATE: ID / TIME 型刷新游标策略
- 事件查询与水位对齐

## 1. 目标
指导在不同操作类型 (HARVEST / BACKFILL / UPDATE) 下选择与推进 cursor 的模式，并安全执行回放与窗口推进。

## 2. 模式选择
| operation | primary namespace | 次级 | 说明 |
|-----------|------------------|------|------|
| HARVEST | GLOBAL | EXPR | 全量基线，表达式细化可选 |
| BACKFILL | CUSTOM:{plan_key} | - | 与主线隔离，避免污染 lag |
| UPDATE | EXPR | GLOBAL | 个性表达式增量，必要时回退 GLOBAL |

## 3. 前向推进流程
1. 读取 namespace 当前 cursor_value。
2. 任务批次完成 → 计算批次最大边界 (max(offset), max(id), max(changed_at- safetyLag))。
3. 生成 cursor_event(type=FORWARD, from, to, reason=BATCH_COMPLETE)。
4. CAS 更新 cursor 表 (WHERE current_value = from)。
5. 记录 metrics: forward_distance, lag_sec。

## 4. 滞后 (Lag) 计算
`lag_sec = now() - cursor_value` (时间窗口)；若为 OFFSET/ID 模式，则换算为 last_seen_time 进行混合度量。

## 5. 回放 (Replay) 使用
| 场景 | 条件 | 动作 |
|------|------|------|
| 补偿晚到数据 | 发现漏斗 (下游缺口) | 创建 REPLAY slice (from=cursor_value - safetyLag, to=cursor_value) |
| 策略变更 | 分页模式升级 | 生成 REPLAY 仅覆盖最近窗口 |
| 数据污染修复 | L3 清洗后 | REPLAY 指定时间段 |

## 6. 回放安全检查
| 检查 | 目的 |
|------|------|
| 重复次数 <= 配额 | 防止无限回放 |
| 与当前窗口不交叉 | 避免覆盖写 | 
| cursor_event 链连续 | 确保没有跳跃 |

## 7. 命名空间演进策略
| 阶段 | 行为 |
|------|------|
| 初始 (无 EXPR) | 用 GLOBAL 快速全量 |
| 差异稳定 | 拆出高频表达式为 EXPR 游标 | 
| 多表达式规模化 | 合并低频 EXPR → GLOBAL 降维护成本 |

## 8. 常见陷阱
| 现象 | 原因 | 解决 |
|------|------|------|
| BACKFILL 游标推进主窗口 | namespace 用错 | 使用 CUSTOM 前缀 |
| 回放误推进 | 未校验 event chain | 加 CAS & 连续性检查 |
| lag 波动大 | 混合模式换算误差 | 统一用时间主标尺 |

## 9. Checklist
- [x] 模式与 namespace 选择
- [x] 前向推进步骤
- [x] 回放与安全检查
- [x] 命名空间演进
- [x] 常见陷阱
