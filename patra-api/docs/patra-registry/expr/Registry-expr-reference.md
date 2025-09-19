# Papertrace Registry · Expr Reference

导航： [体系总览](../README.md) ｜ 同域： [Expr Guide](Registry-expr-guide.md) ｜ [Expr Usage](Registry-expr-usage.md)

## 目录
- [1. 表结构与要点](#sec-1)
  - [1.1 `reg_expr_field_dict`（统一字段字典）](#sec-1-1)
  - [1.2 `reg_prov_api_param_map`（API 参数映射）](#sec-1-2)
  - [1.3 `reg_prov_expr_capability`（字段能力）](#sec-1-3)
  - [1.4 `reg_prov_expr_render_rule`（渲染规则）](#sec-1-4)
- [2. 生效规则与读侧查询模板](#sec-2)


## <a id="sec-1"></a> 1. 表结构与要点

### <a id="sec-1-1"></a> 1.1 `reg_expr_field_dict`（统一字段字典）
**用途**：系统内部字段统一语义的唯一事实来源；无源敏感。

关键列：
- `field_key`：统一内部字段键（小写蛇形/缩写），唯一。
- `data_type_code`：`DATE/DATETIME/NUMBER/TEXT/KEYWORD/BOOLEAN/TOKEN`。
- `cardinality_code`：`SINGLE/MULTI`。
- `exposable`：全局可暴露开关（与来源能力层区分）。
- `is_date`：冗余标记，便于 UI/DateLens 快速判断。

索引：`UK(field_key)`，`idx(updated_at)`。

---

### <a id="sec-1-2"></a> 1.2 `reg_prov_api_param_map`（API 参数映射）
**用途**：将标准键（统一内部语义）映射为供应商参数名（HTTP 层）。

维度与时间片唯一键：
```
(provenance_id, scope_code, task_type_key, operation_code, std_key, effective_from)
```

关键列：
- `scope_code`：`SOURCE`/`TASK`（可扩展）。
- `task_type`/`task_type_key`：任务类型，`NULL→ALL`（生成列）。
- `operation_code`：`SEARCH/DETAIL/LOOKUP`。
- `std_key`：标准键（通常来自 `reg_expr_field_dict.field_key`）。
- `provider_param_name`：供应商参数名。
- `transform_code`：值级转换（如 `TO_EXCLUSIVE_MINUS_1D`）。

索引：`idx(provenance_id, operation_code, std_key)`；反查 `idx(provider_param_name)`。

---

### <a id="sec-1-3"></a> 1.3 `reg_prov_expr_capability`（字段能力）
**用途**：声明某来源在特定字段上的可用操作与限制。

维度与时间片唯一键：
```
(provenance_id, scope_code, task_type_key, field_key, effective_from)
```

关键列：
- `ops`：允许的表达式操作集合，如 `["TERM","IN","RANGE"]`。
- `term_*`/`in_*`/`range_*`：匹配策略、大小写、最小长度/最大长度/集合大小上限、范围类型与边界等。
- `range_kind_code`：`NONE/DATE/DATETIME/NUMBER`。

索引：`idx(provenance_id, field_key)`；`idx(updated_at)`。

---

### <a id="sec-1-4"></a> 1.4 `reg_prov_expr_render_rule`（渲染规则）
**用途**：把 Expr 原子渲染为 query 片段或 params；不重复做键名映射。

维度与时间片唯一键（引入归一化列消除 NULL 歧义）：
```
(provenance_id, scope_code, task_type_key, field_key,
 op_code, match_type_key, negated_key, value_type_key,
 emit_type_code, effective_from)
```

关键列：
- `op_code`：`TERM/IN/RANGE/EXISTS/TOKEN`。
- `emit_type_code`：`QUERY` 或 `PARAMS`。
- `match_type_key`：`match_type_code` 的归一化（`NULL→ANY`）。
- `negated_key`：`negated` 的归一化（`T/F/ANY`）。
- `value_type_key`：`value_type_code` 的归一化（`NULL→ANY`）。
- `template/item_template/joiner/wrap_group`：用于 `QUERY` 渲染。
- `params/fn_code`：用于 `PARAMS` 渲染（参数名由 `param_map` 统一解析）。

索引：`idx(provenance_id, field_key, op_code)`；`idx(updated_at)`。

---


## <a id="sec-2"></a> 2. 生效规则与读侧查询模板

**当前生效**判定：
```sql
WHERE deleted=0
  AND NOW() BETWEEN effective_from AND COALESCE(effective_to,'9999-12-31')
ORDER BY effective_from DESC
LIMIT 1;
```

**参数名映射**：
```sql
SELECT m.provider_param_name, m.transform_code
FROM reg_prov_api_param_map m
WHERE m.deleted=0
  AND m.provenance_id=:provId
  AND m.scope_code=:scopeCode
  AND m.task_type_key=:taskKey
  AND m.operation_code=:opCode
  AND m.std_key=:stdKey
  AND NOW() BETWEEN m.effective_from AND COALESCE(m.effective_to,'9999-12-31')
ORDER BY m.effective_from DESC
LIMIT 1;
```

**字段能力**：
```sql
SELECT c.ops, c.term_matches, c.range_kind_code, c.in_max_size, c.range_allow_open_end, ...
FROM reg_prov_expr_capability c
WHERE c.deleted=0
  AND c.provenance_id=:provId
  AND c.scope_code=:scopeCode
  AND c.task_type_key=:taskKey
  AND c.field_key=:fieldKey
  AND NOW() BETWEEN c.effective_from AND COALESCE(c.effective_to,'9999-12-31')
ORDER BY c.effective_from DESC
LIMIT 1;
```

**渲染规则**：
```sql
SELECT r.emit_type_code, r.template, r.item_template, r.joiner, r.wrap_group, r.params, r.fn_code
FROM reg_prov_expr_render_rule r
WHERE r.deleted=0
  AND r.provenance_id=:provId
  AND r.scope_code=:scopeCode
  AND r.task_type_key=:taskKey
  AND r.field_key=:fieldKey
  AND r.op_code=:opCode
  AND r.match_type_key=:matchKey  -- 'ANY' 表示不区分
  AND r.negated_key=:negKey       -- 'T'/'F'/'ANY'
  AND r.value_type_key=:valKey    -- 'STRING'/'DATE'/'DATETIME'/'NUMBER'/'ANY'
  AND NOW() BETWEEN r.effective_from AND COALESCE(r.effective_to,'9999-12-31')
ORDER BY r.effective_from DESC
LIMIT 1;
```

---
