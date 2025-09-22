# Ingest · Strategy Ops

导航：Strategy Schema | Guide | Reference | Strategy[本页] | Examples ｜ 返回：`../README.md`

聚焦运行中策略级调优：在线再切片、AIMD 降速、预算回退、安全延迟自适应。

## 运维主题
- 429 / 5xx 自适应：demoteRate 与 r_min 观察
- Re-slice 触发与执行闭环
- SafetyLag 动态调优（晚到率 vs 吞吐）
- 预算耗尽与优先级回退

## 1. 运行期调优目标
在不牺牲数据完整性的前提下最大化吞吐并最小化重试与窗口滞后。

## 2. AIMD 参数动态调节
| 指标 | 条件 | 动作 | 说明 |
|------|------|------|------|
| success_rate > 99% 且 lag 增长 <5% | 5 连续窗口 | rate += additive | 温和提升 |
| L1 重试率 1%~5% | 即时 | 不调整 | 正常波动 |
| L1 重试率 >5% | 触发 | rate *= multiplicative | 快速降速 |
| L2 错误 >1% | 触发 | rate *= 0.3 + 降级分页评估 | 避免放大失败 |

## 3. SafetyLag 重估
| 场景 | 判定 | 新值限制 |
|------|------|----------|
| 来源延迟升高 | p95 > safetyLag*0.8 | 新值=min(p99, old*1.5) |
| 延迟降低 | p95 < safetyLag*0.5 持续2周期 | 新值=max(base, old*0.7) |

## 4. 预算回退策略
| 现象 | 策略 |
|------|------|
| 预算即将耗尽 (剩余<10%) | 请求扩容或切换 BACKFILL 分段 |
| 已超预算 | STOP 计划 & 创建补充 plan | 避免隐性成本 |
| 高频再切片 | 调整初始预算估公式权重 | 降低误判 |

## 5. 分页模式降级 Runbook
1. 监控发现 TOKEN 模式连续 N 次 (N=3) 获取下一页失败或不稳定 (响应时间 > p95*2)。
2. 写入策略事件表 (type=DOWNGRADE, from=TOKEN,to=OFFSET)。
3. 更新后续派生任务的分页参数 (保持已完成批次不变)。
4. 记录 fingerprint 变化（仅 pagination_mode 位）。
5. 观察 2 个窗口内错误与 lag，必要时继续降级到 ID_RANGE。

## 6. 指标告警建议
| 指标 | 阈值 | 告警级别 | 行动 |
|------|------|----------|------|
| strategy_rate_current / desired | <0.5 持续10m | 警告 | 检查限流或错误激增 |
| safety_lag_sec | > 上次 *1.5 | 警告 | 验证来源延迟是否真实升高 |
| downgrade_events_count | >0 in 1h | 提醒 | 审核降级原因 |
| budget_consumption_ratio | >0.9 | 警告 | 申请扩容 / 分段 |

## 7. 常见故障排查
| 症状 | 可能原因 | 诊断 SQL | 措施 |
|------|----------|----------|------|
| 频繁降级到 OFFSET | TOKEN 不稳定 | 检查 API 错误分布 | 调整 rate 或延长滞后 |
| lag 无法下降 | 窗口过大 / 安全滞后过高 | 查看窗口与延迟统计 | 重新编译窗口或减滞后 |
| 预算长期饱和 | 估算偏差 | 比较估算 vs 实际 | 优化估算模型 |

## 8. Checklist
- [x] AIMD 调参
- [x] SafetyLag 重估
- [x] 预算回退
- [x] 分页降级 Runbook
- [x] 指标告警 & 故障排查
