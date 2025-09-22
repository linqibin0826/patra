# Ingest · Strategy Guide

导航：Strategy Schema | Strategy[本页] | Reference | Ops | Examples ｜ 返回：`../README.md`

面向工程选择：如何在不同来源特性下组合分页/窗口/两段式/预算/限流/重试策略。提供决策树与示例。

## 决策主题
- 分页类型选择：OFFSET vs PAGE vs TOKEN vs URL vs TIME
- 时间窗策略与 SafetyLag 估算
- 两段式判定：搜索响应字段稀疏/ID 分离
- 限流与配额调度：Rate Gate + Budgeter 协作
- 在线再切片触发阈值建议

## 1. 目的
定义编译期间如何在窗口、分页、预算、速率及降级链之间决策，输出稳定且可回放的 Strategy Snapshot。

## 2. 总体流程
1. 解析表达式 → AST 校验。
2. 来源能力矩阵加载 (strategy-schema-design §2)。
3. 预估数据量 (基于历史/索引统计)。
4. 选择分页模式 (OFFSET / TOKEN / COMPOSITE)。
5. 计算窗口分割与 safetyLag。
6. 预算与 AIMD 初始 rate 设定。
7. 降级链组装 (TOKEN→OFFSET→ID_RANGE)。
8. 生成 fingerprint + snapshot 持久化。

## 3. 分页决策树
```
		  +-- supports token? -- Yes --> TOKEN
		  |                        |
Start --> +-- No ------------------+-- has stable numeric id? -- Yes --> OFFSET
								   |                               |
								   +-- No --------------------------+--> COMPOSITE (双维度时间+ID)
```

## 4. 窗口与 SafetyLag
| 输入 | 逻辑 | 结果 |
|------|------|------|
| operation=UPDATE | 使用 changedSince 窗口 | from = last_cursor - safetyLag |
| operation=HARVEST | 初始全量 | from=epoch 或配置起点 |
| safetyLag 估算 | max(来源延迟 p99, 业务回写 SLA) | 确保不跳过晚到记录 |

## 5. 预算 (Budget) 与 初始速率
| 步骤 | 描述 |
|------|------|
| 预估记录数 | 基于最近窗口吞吐 * 窗口长度 |
| 分配预算 | 若估算>全局配额 → 拆分窗口或 BACKFILL |
| 初始速率 | min(来源 quota / 并发, 内部最大速率) |
| AIMD 参数 | additive = floor(rate*0.05), multiplicative=0.5 |

## 6. 降级链构建
| 优先级 | 模式 | 触发降级条件 |
|--------|------|--------------|
| 1 | TOKEN | 连续 TOKEN 不可用 / 不稳定 | 
| 2 | OFFSET | 受限: limit>max / 深度性能差 |
| 3 | ID_RANGE | OFFSET 再失败或需要分治 |

## 7. Fingerprint 组成
`{provenance_code}:{operation}:{expr_proto_hash}:{strategy_bits}:{pagination_mode}:{window_hash}`

## 8. 决策一致性保障
| 风险 | 缓解 |
|------|------|
| 重编译导致不同分页模式 | 指纹比较，若 core 语义未变保持旧模式 |
| 安全滞后漂移 | 设最大调整步长 (<= previous * 1.5) |
| 预算频繁变动 | 预算锁定窗口内不可变更，需新 plan |

## 9. 输出 Snapshot 字段映射
| 字段 | 来源 |
|------|------|
| pagination_mode | 决策树 §3 |
| window_from/to | 窗口策略 §4 |
| safety_lag_sec | 延迟估算 §4 |
| initial_rate | 预算 & 速率 §5 |
| downgrade_chain | 降级链 §6 |
| fingerprint | 组合 §7 |

## 10. Checklist
- [x] 分页决策树
- [x] 窗口与安全滞后
- [x] 预算与速率初始化
- [x] 降级链逻辑
- [x] Fingerprint 定义
- [x] 输出映射表
