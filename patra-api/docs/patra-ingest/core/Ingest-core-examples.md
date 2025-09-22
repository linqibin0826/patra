# Ingest · Core Examples

导航：Core Schema | Guide | Reference | Ops | Core[本页] ｜ 返回：`../README.md`

## 场景 1 · HARVEST 时间窗口
输入：
```
provenance=pubmed
operation=HARVEST
expression=(title:"cancer" AND lang:en)
window.from=2025-09-01T00:00:00Z
window.to  =2025-09-02T00:00:00Z
safetyLag=3600s
slice.strategy=TIME step=30m
```
裁剪：effective_to = min(window.to, now-safetyLag) 假设 now=2025-09-03 → 使用 window.to。
切片（伪）：[00:00,00:30) ... [23:30,24:00) 共 48 片。
示例 slice_spec：
```json
{
	"type":"TIME",
	"from":"2025-09-01T08:00:00Z",
	"to":"2025-09-01T08:30:00Z"
}
```
局部化表达式（片中之一）：
```
(title:"cancer" AND lang:en) AND updated_at:[2025-09-01T08:00:00Z TO 2025-09-01T08:30:00Z)
```

## 场景 2 · BACKFILL 补历史
输入：operation=BACKFILL, window.from=2024-01-01, window.to=2024-02-01，step=1d。
切片数=31；与 HARVEST 区别：命名空间使用 CUSTOM(backfill_campaign_hash)，不推进前向 cursor。

## 场景 3 · UPDATE ID 段切片
目标：对已知 ID 范围 [100000, 101000) 分批重抓。
策略：slice.strategy=ID_RANGE, params.step=100。
生成： [100000,100100), [100100,100200) ... [100900,101000)。
局部化表达式示例：
```
expr_proto AND id:[100200 TO 100300)
```

## 场景 4 · Plan / Slice / Task 样板
Plan (节选)：
```json
{
	"plan_key":"harvest_pubmed_20250901_24h",
	"operation_code":"HARVEST",
	"provenance_code":"pubmed",
	"window_from":"2025-09-01T00:00:00Z",
	"window_to":"2025-09-02T00:00:00Z",
	"slice_strategy_code":"TIME",
	"slice_params":{"stepMinutes":30},
	"status_code":"READY"
}
```
Slice (节选)：
```json
{
	"slice_no":16,
	"slice_spec":{"type":"TIME","from":"2025-09-01T08:00:00Z","to":"2025-09-01T08:30:00Z"},
	"slice_signature_hash":"<sha256>",
	"expr_hash":"<sha256>",
	"status_code":"PENDING"
}
```
Task (节选)：
```json
{
	"priority":5,
	"status_code":"QUEUED",
	"idempotent_key":"<task_sha256>",
	"slice_id":12345,
	"expr_hash":"<sha256>",
	"operation_code":"HARVEST"
}
```

## 决策树速览
切片策略选择：
```
if operation==BACKFILL -> TIME 或 ID_RANGE (历史跨度大优先 TIME)
else if endpoint 支持 TOKEN 分页 & 增量字段=时间 -> TIME
else if 需要精准 ID 重采 -> ID_RANGE
else -> TIME 默认
```

分页降级：
```
TOKEN 失败>=3 次 => OFFSET
OFFSET 深分页性能风险 -> 再切片缩小窗口
```

## Checklist (Examples Completeness)
- [x] HARVEST 时间窗
- [x] BACKFILL 历史
- [x] UPDATE ID 段
- [x] 样板 JSON
- [x] 策略决策树

