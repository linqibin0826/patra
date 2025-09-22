# Ingest · Runtime Examples

导航：Runtime Schema | Guide | Reference | Ops | Runtime[本页] ｜ 返回：`../README.md`

## 示例 1 · 任务租约与重试
| 步 | 状态 | 描述 |
|----|------|------|
| 1 | QUEUED | 初始入队 |
| 2 | RUNNING | Worker A 抢占 lease_owner=A leased_until=+2m |
| 3 | RUNNING | 心跳续租 leased_until=+2m30s |
| 4 | RUNNING→FAILED(run attempt) | 第一次 attempt 异常(L1) |
| 5 | PLANNED→RUNNING (attempt=2) | 自动重试；租约保持 |
| 6 | RUNNING→SUCCEEDED | 成功，task→SUCCEEDED |

## 示例 2 · 批次与分页 TOKEN→OFFSET 降级
| 批次 | 模式 | before_token | after_token | 降级标志 |
|------|------|--------------|-------------|-----------|
| 1 | TOKEN | null | A |  |
| 2 | TOKEN | A | A (重复) | count=1 |
| 3 | TOKEN | A | A (重复) | count=2 |
| 4 | TOKEN→OFFSET | (降级) | offset=0 | degrade=token->offset |
| 5 | OFFSET | offset=0 | offset=200 |  |

## 示例 3 · 再切片触发
条件：某 slice 执行耗时 > 30m 且 record_count > 均值 5×。
动作：
1. 原 slice 标记 PARTIAL
2. 生成新 slices：将剩余窗口拆分更细 (15m)
3. 派生新 tasks 入队
4. 观察 reslice.count 指标

## 示例 4 · 批次失败隔离
| 批次 | status | 错误类型 | 动作 |
|------|--------|----------|------|
| 12 | FAILED | L3 schema mismatch | 写入隔离池；不计入 upsert |
| 13 | FAILED | L1 timeout | 将重试新批次 batch_no=14 |
| 14 | SUCCEEDED | - | run 汇总成功 |

## 示例 5 · Cursor 前进与任务完成联动
当最后一个批次提交并触发 cursor.advance：记录 run.stats -> 写 cursor_event -> CAS 更新 ing_cursor；随后调度器检查 plan 是否完成。

## Checklist
- [x] 租约/重试
- [x] TOKEN 降级 OFFSET
- [x] 再切片
- [x] 隔离池处理
- [x] Cursor 联动
