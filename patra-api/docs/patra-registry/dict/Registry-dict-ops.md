# Papertrace Registry · 字典（Dict）Ops / Runbook

导航： [体系总览](../README.md) ｜ 同域： [字典 Guide](Registry-dict-guide.md) ｜ [字典 Reference](Registry-dict-reference.md)

## 目录
- [1. 读写实践与健康检查](#sec-1)
  - [1.1 常用查询](#sec-1-1)
  - [1.2 数据质量巡检（应返回 0 行）](#sec-1-2)
- [2. 运维与治理（Runbook）](#sec-2)
- [3. FAQ 与取舍](#sec-3)


## <a id="sec-1"></a> 1. 读写实践与健康检查

### <a id="sec-1-1"></a> 1.1 常用查询

```sql
-- 取指定类型 + 编码
SELECT item_id, type_code, item_code, display_name
FROM v_sys_dict_item_enabled
WHERE type_code = 'http_method'
  AND item_code = 'GET';

-- 取某类型默认项
SELECT item_id, item_code
FROM v_sys_dict_item_enabled
WHERE type_code = 'endpoint_usage'
  AND is_default = 1;

-- 列出可用项（按显示顺序）
SELECT item_id, item_code, display_name
FROM v_sys_dict_item_enabled
WHERE type_code = 'retry_after_policy'
ORDER BY display_order;
```

### <a id="sec-1-2"></a> 1.2 数据质量巡检（应返回 0 行）

```sql
-- A. 检查“默认项>1”的违规
SELECT dt.type_code, COUNT(*) AS defaults
FROM sys_dict_item di
         JOIN sys_dict_type dt ON dt.id = di.type_id
WHERE di.is_default = 1
  AND di.enabled = 1
  AND di.deleted = 0
  AND dt.deleted = 0
GROUP BY dt.type_code
HAVING COUNT(*) > 1;

-- B. 检查“同类型重复 item_code”（理论上被唯一键阻止，这里兜底）
SELECT dt.type_code, di.item_code, COUNT(*) c
FROM sys_dict_item di
         JOIN sys_dict_type dt ON dt.id = di.type_id
GROUP BY dt.type_code, di.item_code
HAVING c > 1;
```

---


## <a id="sec-2"></a> 2. 运维与治理（Runbook）

- **新增类型**：`sys_dict_type` 插入一行；尽量避免删除，使用 `deleted=1` 软删。
- **新增项目**：`sys_dict_item` 插入；如要设为默认，将其他项目的 `is_default` 更新为 0（唯一约束可防止并发冲突）。
- **废弃项目**：将 `enabled=0` 或 `deleted=1`；不建议修改 `item_code`（稳定键）。
- **外部映射**：在 `sys_dict_item_alias` 维护第三方值映射，避免在业务表“硬编码对照表”。
- **发布审计**：使用 `record_remarks` 记录变更原因（JSON 数组）。
- **定期巡检**：执行 [§1.2](#sec-1-2) 的健康检查 SQL。

---


## <a id="sec-3"></a> 3. FAQ 与取舍

- **问：为何不直接用 CHECK 强约束“类型匹配”？**  
  MySQL 8.0 的 CHECK 不能引用子查询，无法在数据库层保证 `http_method` 等字段一定属于某个 `type_code`。实践采用**应用层校验 + 巡检 SQL**。
- **问：默认项如何防止并发写冲突？**  
  `default_key` + 唯一键天生防止；并发设置多个默认将失败，应用层捕获后重试或回滚。
