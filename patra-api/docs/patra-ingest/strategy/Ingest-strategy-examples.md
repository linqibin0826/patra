# Ingest · Strategy Examples

导航：Strategy Schema | Guide | Reference | Ops | Strategy[本页] ｜ 返回：`../README.md`

## 示例 1 · PubMed 基础策略 (OFFSET)
输入：pagination=OFFSET pageSize=200; slice.strategy=TIME step=30m; window=24h; safetyLag=1h。
决策：
- endpoint 不支持 token → OFFSET
- step=30m → 48 片；pageSize=200 估算批次数= 预计总记录/200
降级记录：无。

## 示例 2 · Crossref TOKEN + changedSince
输入：pagination=TOKEN nextTokenPath=$.next; filter.changedSince=cursor(updated_at); slice.strategy=TIME step=15m。
流程：
1. 计算 effective_to = now - safetyLag
2. 每 slice 本地化表达式含 updated_at 区间
3. batch: before_token=null → token1 → token2... 直到 next 缺失
降级条件：连续 3 批 nextToken 缺失提前结束。

## 示例 3 · 两段式（Search→Detail）+ 预算
假设：Search 调用 cost=1，Detail 调用 cost=3（每条记录需要详情）。
策略：
| 阶段 | AIMD 初始 baseRps | 预算 daily | 降速触发 |
|------|------------------|-----------|-----------|
| Search | 20 | 50k | L1 比例>30% or 429 增长 |
| Detail | 60 | 150k | L1 比例>25% |
调度：先完成全部 Search slices → 汇总 ID → 分批 Detail 运行。若日预算不足：截断剩余 Detail，写 PARTIAL。

## 示例 4 · 再切片前后对比
原 slice：TIME step=2h；某 slice 执行>40m 且 record_count>>均值。
再切片：将该窗口 [08:00,10:00) 拆成 8 × 15m 细粒度；原 slice 标记 PARTIAL，新 slices 补齐剩余数据。
收益：并行度 ↑，单批延迟 ↓，滞后恢复。

## 示例 5 · 预算 + 速率联动
若剩余 dailyBudget < (预计剩余 slice * avgCost)，提前缩小窗口 effective_to，减少无效拉取；触发 backfill 计划补全。

## 策略决策骨架
```
resolveWindow()
chooseSliceStrategy()
applyPagination()
applyRateLimit()
applyBudget()
validateAndDegrade()
fingerprint()
```

## Checklist
- [x] OFFSET 场景
- [x] TOKEN 场景
- [x] 两段式 + 预算
- [x] 再切片对比
- [x] 预算联动

