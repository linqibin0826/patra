# Ingest · Cursor Reference

导航：Cursor Schema | Guide | Cursor[本页] | Ops ｜ 总览：`../README.md`

## 1. 表字段与索引摘要
### ing_cursor
| 列 | 类型 | 关键语义 | 备注 |
|----|------|----------|------|
| provenance_code | VARCHAR(64) | 来源 | 与 reg_provenance 逻辑关联 |
| operation_code | VARCHAR(32) | 操作 | HARVEST/BACKFILL/UPDATE/METRICS |
| cursor_key | VARCHAR(64) | 水位键 | 如 updated_at / published_at / seq_id / cursor_token |
| namespace_scope_code | VARCHAR(32) | 命名空间域 | GLOBAL/EXPR/CUSTOM |
| namespace_key | CHAR(64) | 命名空间键 | expr_hash 或自定义 hash，global=全0 |
| cursor_type_code | VARCHAR(32) | 类型 | TIME / ID / TOKEN |
| cursor_value | VARCHAR(1024) | 当前值 | 原始字符串（ISO / 十进制 / 不透明） |
| observed_max_value | VARCHAR(1024) | 观测最大 | 用于滞后/落后监测 |
| normalized_instant | TIMESTAMP(6) | 归一时间值 | cursor_type=TIME 填 |
| normalized_numeric | DECIMAL(38,0) | 归一数值 | cursor_type=ID 填 |
| schedule_instance_id..last_batch_id | BIGINT | lineage | 最近推进链路（调试/回放） |
| expr_hash | CHAR(64) | 最近推进表达式 | 判断变更溯源 |
| version | BIGINT | 乐观锁 | CAS 前向推进 |

索引重点：
- uk_cursor_ns(provenance_code,operation_code,cursor_key,namespace_scope_code,namespace_key)
- 排序：idx_cursor_sort_time / idx_cursor_sort_id 支持范围检索
- lineage：idx_cursor_lineage 支持回溯一跳查询

### ing_cursor_event
| 列 | 语义 | 备注 |
|----|------|------|
| prev_value/new_value | 前后值 | 前值可空=首次 |
| prev_instant/new_instant | 时间型归一 | 用于重放/差值计算 |
| prev_numeric/new_numeric | 数值型归一 | 同上 |
| window_from/window_to | 覆盖窗口 | `[from,to)` 半开；可空（ID/TOKEN） |
| direction_code | 推进方向 | FORWARD/BACKFILL |
| idempotent_key | 幂等 | 防止重复事件插入 |
| lineage( schedule..batch_id ) | 来源链路 | 诊断与回放配对 |
| expr_hash | 推进使用表达式 | 变更影响评估 |

事件索引：
- uk_cur_evt_idem(idempotent_key)
- idx_cur_evt_timeline(来源+操作+cursor_key+namespace...)：过滤主维度
- idx_cur_evt_instant / idx_cur_evt_numeric：重放、统计
- idx_cur_evt_window：窗口对齐查询

## 2. 幂等键公式
| 层 | 公式 | 说明 |
|----|------|------|
| Event | SHA256(source,op,key,ns_scope,ns_key,prev->new,window,run_id,batch_id) | 事件先行：一次逻辑推进只记一条 |
| Cursor(CAS) | 条件：new > old（按类型比较） | 仅前进；否则不更新 |

TIME 比较：normalized_instant；ID 比较：normalized_numeric；TOKEN：只要值不同且业务定义“可接受前进”即可（通常记录但不回退）。

## 3. 推进算法（TIME/ID 型）
```
function advance(namespace, batchContext):
	cur = loadCursor(namespace) // 可能为空
	candidate = deriveCandidate(batchContext) // 来自本批 processed records
	if cur != null and !isForward(cur, candidate):
			appendEvent(cur, cur, reason="no-forward", batchContext)
			return cur
	// 先写事件（审计先行）
	evt = buildEvent(prev=cur, new=candidate, batchContext)
	insertEvent(evt) // 幂等: idempotent_key 约束
	// CAS 前进
	success = upsertOrCompareAndSwap(namespace, prevVersion=cur?.version, newValue=candidate)
	if !success:
			// 并发：读取最新；若最新 >= candidate 则无需再推进；否则重试(指数退避 3 次)
			latest = loadCursor(namespace)
			if isForward(latest, candidate):
					retry advance(namespace, batchContext)
	return loadCursor(namespace)
```

TOKEN 模式：candidate 不可排序；策略：记录事件 + 覆盖 cursor_value；若并发冲突以“最后写入”与后续 batch 校验是否失效（若失效补一条补偿事件）。

## 4. 回放算法
```
function replay(namespace, fromTime=null, toTime=null):
	events = scanEvents(namespace, fromTime, toTime) order by id asc
	state = null
	for e in events:
		 if state == null or isForward(state, e.new):
				 state = e.new
	return state
```
一致性校验：replay(namespace) 应 == ing_cursor 当前值；否则触发告警 cursor.rebuild.required。

## 5. 异常与保护
| 场景 | 处理 | 监控指标 |
|------|------|----------|
| 并发高冲突 | 限制重试次数后放弃（不回退） | cursor.cas.conflict.count |
| 候选倒退 | 写 no-forward 事件 | cursor.no_forward.count |
| TOKEN 失效 | 记录补偿事件并刷新 | cursor.token.compensate.count |
| 回放缺口 | 标记潜在审计丢失 | cursor.replay.gap.count |

## 6. 与安全滞后(SafetyLag)的关系
推进到 effectiveTo（now - safetyLag）或 slice 界限上限；observed_max_value 记录已见最大端点值，用于评估 lag = observed_max - cursor_value。

## 7. 指标建议
| Metric | 描述 | 标签(labels) | 计算 |
|--------|------|--------------|------|
| cursor.lag.seconds | 当前滞后秒数 | provenance,operation,cursor_key,ns_scope | observed_max_instant - normalized_instant |
| cursor.advance.count | 推进次数 | provenance,operation,cursor_key | 事件成功写入计数 |
| cursor.no_forward.count | 候选未前进 | provenance,operation,cursor_key | prev>=new |
| cursor.cas.conflict.count | CAS 冲突 | provenance,operation,cursor_key | 更新失败且需重试 |
| cursor.replay.duration.ms | 回放耗时 | provenance,operation,cursor_key | 重放函数耗时 |

## 8. 使用清单 (Checklist)
1. 只读查询使用 ing_cursor，不扫事件表。
2. 审计/回放/诊断进入事件表（限定 namespace+时间窗口）。
3. 不提供删除事件接口；合规清理需归档后再分区清除（未来扩展）。
4. TOKEN 模式需下游幂等保证（record key 或 upsert）。
5. 指标 lag 超阈值触发 BACKFILL 或告警路线。
