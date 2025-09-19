# Papertrace Registry · Expr Usage & Examples

导航： [体系总览](../README.md) ｜ 同域： [Expr Guide](Registry-expr-guide.md) ｜ [Expr Reference](Registry-expr-reference.md)

## 目录
- [1. 使用流程（Adapter → App → Domain）](#sec-1)
- [2. 示例（Publish Date 范围检索）](#sec-2)
- [3. 质量保障（Lint / 自检）](#sec-3)
- [4. 与 `reg_prov_*` 的边界](#sec-4)
- [5. 运行与运维建议](#sec-5)
- [6. FAQ](#sec-6)
- [7. 变更清单（与初版差异）](#sec-7)


## <a id="sec-1"></a> 1. 使用流程（Adapter → App → Domain）

1. 将外部输入解析为统一 **Expr**（`field_key`、`op_code`、值、`match_type`、`negated` 等）。
2. 从 **capability** 读取当前生效配置，验证该 `field_key + op_code` 是否被允许，校验长度/大小写/范围等。
3. 若 **param_map** 为该 `std_key/operation_code` 提供了 `transform_code`，先对值做**值级转换**。
4. 从 **render_rule** 读取渲染模板：
   - `emit=QUERY`：使用 `template/item_template/joiner/wrap_group` 生成查询片段；
   - `emit=PARAMS`：生成标准键的参数集合（如 `from/to`）。
5. 使用 **param_map** 将标准键替换为供应商参数名（如 `from → mindate`）。
6. 与 `reg_prov_*` 端点/分页配置组合，发起 HTTP 调用。

> 推荐顺序：**capability → transform → render → param_map**。

---


## <a id="sec-2"></a> 2. 示例（Publish Date 范围检索）

**统一字段**：
```sql
INSERT INTO reg_expr_field_dict(id, field_key, data_type_code, cardinality_code, exposable, is_date)
VALUES (1001, 'publish_date', 'DATE', 'SINGLE', 1, 1);
```

**参数名映射（PubMed SEARCH）**：
```sql
INSERT INTO reg_prov_api_param_map
(id, provenance_id, scope_code, task_type, operation_code, std_key, provider_param_name, transform_code, effective_from)
VALUES
(2001, 1, 'SOURCE', NULL, 'SEARCH', 'from', 'mindate', NULL, '2025-01-01 00:00:00'),
(2002, 1, 'SOURCE', NULL, 'SEARCH', 'to',   'maxdate', 'TO_EXCLUSIVE_MINUS_1D', '2025-01-01 00:00:00');
```

**字段能力**：
```sql
INSERT INTO reg_prov_expr_capability
(id, provenance_id, scope_code, task_type, field_key, effective_from, ops, range_kind_code, range_allow_open_end)
VALUES
(3001, 1, 'SOURCE', NULL, 'publish_date', '2025-01-01 00:00:00',
  JSON_ARRAY('RANGE'), 'DATE', 1);
```

**渲染规则（PARAMS）**：
```sql
INSERT INTO reg_prov_expr_render_rule
(id, provenance_id, scope_code, task_type, field_key, op_code, emit_type_code, value_type_code,
 effective_from, params, fn_code)
VALUES
(4001, 1, 'SOURCE', NULL, 'publish_date', 'RANGE', 'PARAMS', 'DATE',
 '2025-01-01 00:00:00',
 JSON_OBJECT('from','from','to','to'), 'PUBMED_DATETYPE');
```

> 执行流：Expr {{field_key=publish_date, op=RANGE, from=2025-01-01, to=2025-02-01}} → 渲染为标准键 {{from:..., to:...}} → param_map 映射为 {{mindate:..., maxdate:...}}，并对 `to` 应用 `TO_EXCLUSIVE_MINUS_1D`。

---


## <a id="sec-3"></a> 3. 质量保障（Lint / 自检）

**时间片重叠**（以 param_map 为例）：
```sql
SELECT a.id AS a_id, b.id AS b_id, a.provenance_id, a.std_key, a.operation_code,
       a.effective_from a_from, a.effective_to a_to, b.effective_from b_from, b.effective_to b_to
FROM reg_prov_api_param_map a
JOIN reg_prov_api_param_map b
  ON a.id<>b.id AND a.deleted=0 AND b.deleted=0
 AND a.provenance_id=b.provenance_id
 AND a.scope_code=b.scope_code AND a.task_type_key=b.task_type_key
 AND a.operation_code=b.operation_code AND a.std_key=b.std_key
 AND COALESCE(a.effective_to,'9999-12-31') > b.effective_from
 AND COALESCE(b.effective_to,'9999-12-31') > a.effective_from;
```

**能力-渲染不匹配**：
```sql
SELECT c.provenance_id, c.field_key
FROM reg_prov_expr_capability c
LEFT JOIN reg_prov_expr_render_rule r
  ON r.provenance_id=c.provenance_id
 AND r.field_key=c.field_key
 AND r.op_code='TERM'
 AND NOW() BETWEEN r.effective_from AND COALESCE(r.effective_to,'9999-12-31')
WHERE c.deleted=0
  AND JSON_CONTAINS(c.ops, JSON_ARRAY('TERM'))
  AND NOW() BETWEEN c.effective_from AND COALESCE(c.effective_to,'9999-12-31')
  AND r.id IS NULL;
```

**渲染规则中的参数名硬编码检查**：
```sql
SELECT id, provenance_id, params
FROM reg_prov_expr_render_rule
WHERE deleted=0 AND params IS NOT NULL
  AND JSON_SEARCH(JSON_EXTRACT(params, '$'), 'one', '%mindate%') IS NOT NULL;
```

---


## <a id="sec-4"></a> 4. 与 `reg_prov_*` 的边界

- **端点/分页/HTTP/限流/重试/凭证**：由 `reg_prov_*` 维护。
- **表达式统一与渲染**：由本子域维护。参数名映射只在 `reg_prov_api_param_map`。渲染规则不重复做键名映射。
- **操作名**：端点操作 `reg_operation`（SEARCH/DETAIL/LOOKUP）；表达式操作符 `reg_expr_op`（TERM/IN/RANGE/EXISTS/TOKEN）。两者在代码层用不同的值对象。

---


## <a id="sec-5"></a> 5. 运行与运维建议

 - **灰度**：通过 `scope_code='TASK'` + 指定 `task_type` 生效；稳定后迁移到 `SOURCE/ALL`。
- **版本化**：新增时间片（更新 `effective_from`），不编辑历史段；历史段不必软删。
- **监控**：为“查不到当前生效”的情况记录详细维度键，便于回溯。
- **导入导出**：配置以 `*_code` 与 `field_key` 为主键，适配 GitOps 与多环境迁移。

---


## <a id="sec-6"></a> 6. FAQ

**Q：为什么不用 ENUM？**  
A：`*_code` 来自字典表，便于扩展与跨环境迁移，避免硬编码。

**Q：为什么 render_rule 不做参数名映射？**  
A：为保证职责单一与一致性，参数名映射集中在 `param_map`。

**Q：NULL 如何参与维度匹配？**  
A：通过生成列将 `NULL` 归一化为 `ANY`，保证维度键唯一与查询确定性。

---


## <a id="sec-7"></a> 7. 变更清单（与初版差异）

- 名称统一：`reg_expr_field_dict / reg_prov_api_param_map / reg_prov_expr_capability / reg_prov_expr_render_rule`。
- 去 ENUM：改为 `*_code VARCHAR`，与 `sys_dict_item.item_code` 对齐。
- 增加 `scope_code/task_type/task_type_key/effective_from/effective_to`。
- 维度唯一键改造：渲染规则引入归一化键（`match_type_key/negated_key/value_type_key`）。
- 参数名映射与渲染模板解耦；transform（值级）与 fn（模板级）解耦。

---

**附**：请配合仓库中的 `docs/patra-registry/patra-registry.sql` 使用，确保建表结构与本文一致。
