# Ingest · Cursor Examples

导航：Cursor Schema | Guide | Reference | Ops | Cursor[本页] ｜ 返回：`../README.md`

## 示例 1 · FORWARD 时间游标推进
| 步 | batch_no | window_from | window_to | candidate_new | prev | action |
|----|----------|------------|----------|---------------|------|--------|
| 1  | 1 | 10:00 | 10:05 | 2025-09-01T10:05:00Z | null | insert event + set cursor |
| 2  | 2 | 10:05 | 10:10 | 2025-09-01T10:10:00Z | 10:05 | event + forward |
| 3  | 3 | 10:10 | 10:15 | 2025-09-01T10:12:00Z | 10:10 | event(no-forward? 候选<=prev) -> 不前进 |
| 4  | 4 | 10:15 | 10:20 | 2025-09-01T10:20:00Z | 10:10 | forward |

说明：批次 3 实际最大记录时间 10:12 但窗口到 10:15；候选值 10:12 > 10:10 → forward （若候选≤prev 才 no-forward）。

## 示例 2 · BACKFILL 与前向隔离
-HARVEST cursor: namespace_scope=GLOBAL value=2025-09-01T10:20Z
-BACKFILL plan: namespace_scope=CUSTOM key=bf_<hash>
-回填推进只写 CUSTOM 命名空间 cursor，不影响 GLOBAL。

## 示例 3 · TOKEN 模式事件
| 批次 | before_token | after_token | prev_token | action |
|------|--------------|-------------|------------|--------|
| 1 | null | A | null | event + set cursor_value=A |
| 2 | A | B | A | event + set cursor_value=B |
| 3 | B | (missing) | B | event(no-forward reason=EOF) |

注意：TOKEN 不可排序；只记录 after_token；停在缺失 token。

## 示例 4 · 回放演示
```
replay(namespace):
 events = select * from ing_cursor_event where ns=... order by id
 state=null
 for e in events:
   if state == null or isForward(state,e.new): state=e.new
 return state
```
对比：state == ing_cursor.cursor_value → 一致；否则报警。

## 示例 5 · 滞后计算
observed_max_value=2025-09-01T11:00Z; cursor_value=2025-09-01T10:20Z → lag=40m。
若 lag > 2*safetyLag → WARN；>4*safetyLag → CRITICAL。

## Checklist
- [x] 前向推进
- [x] BACKFILL 隔离
- [x] TOKEN 事件
- [x] 回放算法
- [x] 滞后计算
