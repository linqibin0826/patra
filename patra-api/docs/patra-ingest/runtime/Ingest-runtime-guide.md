# Ingest · Runtime Guide

导航：Runtime Schema | Runtime[本页] | Reference | Ops | Observability ｜ 返回：`../README.md`

端到端执行示例：出队→租约→创建 run→分页批次→游标事件→现值推进→完成/回放。

## 流程纲要
- 取任务 SQL 与排序
- 抢占与续租流程
- 批次循环核心步骤
- 游标推进嵌入点
- 回放与重试入口

## 1. 目标
描述任务执行主循环：出队 → 租约 → 批次派生 → 执行 → 幂等记录 → 游标推进 / 降级 / 回放触发。

## 2. 主循环概览
```
while true:
  task = dequeue_ready_task()
  if not task: sleep(poll_interval); continue
  if acquire_lease(task):
	  batches = plan_batches(task)
	  for b in batches:
		  exec_result = execute_batch(b)
		  persist_run_batch(b, exec_result)
		  if exec_result.transient_errors: schedule_retry(b)
	  finalize_task(task)
	  maybe_forward_cursor(task)
	  maybe_trigger_reslice(task)
```

## 3. 出队 (Dequeue)
| 条件 | 说明 |
|------|------|
| status=PENDING | 待执行任务 |
| scheduled_at <= now | 达到窗口 |
| priority DESC | 优先队列 (core-guide 决策影响) |

## 4. 租约 (Lease)
| 字段 | 作用 |
|------|------|
| lease_owner | 防止多实例并发 |
| leased_until | 超时回收 |
| lease_count | 统计重试与过期 |

## 5. 批次规划 (Batch Planning)
| 输入 | 输出 | 备注 |
|------|------|------|
| slice_spec + strategy.pagination | 一系列批次 (limit/next_token) | TOKEN 优先 |
| rate_limiter.current | 并发与速率上限 | AIMD 动态 |
| budget_remaining | 截断批次数 | 防预算超额 |

## 6. 执行与记录
| 组件 | 责任 |
|------|------|
| executor | 调用外部 API, 解析响应 |
| run_batch_repo | 幂等插入 run + run_batch |
| error_mapper | 错误 → L1~L4 分类 |

## 7. 游标推进 (Cursor Forward)
| 条件 | 动作 |
|------|------|
| 所有成功批次 & 无 L2+ | 计算 forward_to & 写 cursor_event |
| 存在部分失败(L1) | 先不 forward，等待重试 | 保证不跳跃 |
| 回放批次 | 不 forward 主 cursor | 独立统计 |

## 8. 降级与再切片触发
| 检测 | 行为 |
|------|------|
| 连续分页失败 | 写策略降级事件 (strategy-ops §5) |
| 批次大小异常 (过大或全空) | 触发 core reslice 评估 |

## 9. 回放触发
| 现象 | 条件 | 行为 |
|------|------|------|
| 下游缺数据 | 校验缺口窗口 | 创建 REPLAY plan |
| 安全滞后显著降低 | lag 与 safeLag 差距过大 | REPLAY 最近窗口 收敛 |

## 10. 幂等保证
| 层 | 键 | 说明 |
|----|----|------|
| run_batch | idempotent_key(hash(before_token,page_no)) | 防重复执行 |
| cursor_event | (namespace, idempotent_key) | 防多次推进 |

## 11. 失败处理
| 级别 | 处理 |
|------|------|
| L1 | 重试 (指数退避<=max) |
| L2 | 降速或降级分页后重试 |
| L3 | 隔离池 + 人工审计 |
| L4 | 标记任务失败，阻断后续 |

## 12. 指标钩子
| 事件 | 指标 |
|------|------|
| 任务出队 | task_dequeue_count |
| 租约获得 | lease_acquire_latency, lease_conflict_count |
| 批次执行 | batch_latency, batch_size |
| 游标推进 | cursor_forward_distance |
| 降级触发 | strategy_downgrade_events |

## 13. Checklist
- [x] 主循环描述
- [x] 租约/批次/执行/推进
- [x] 降级与再切片
- [x] 回放触发与幂等
- [x] 失败处理层次
- [x] 指标钩子
