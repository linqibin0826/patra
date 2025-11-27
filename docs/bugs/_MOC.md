---
title: Bug 记录
type: moc
updated: 2025-11-27
---

# Bug 记录

## 按状态

### 未解决

```dataview
TABLE severity, date, module
FROM "bugs"
WHERE status = "open"
SORT severity DESC, date DESC
```

### 已解决

```dataview
TABLE severity, date, resolved_at
FROM "bugs"
WHERE status = "fixed"
SORT resolved_at DESC
LIMIT 20
```

## 按严重程度

### Critical / High

```dataview
TABLE date, status, module
FROM "bugs"
WHERE severity = "critical" OR severity = "high"
SORT date DESC
```

## 按模块统计

```dataview
TABLE length(rows) as "Bug 数量"
FROM "bugs"
WHERE file.name != "_MOC"
GROUP BY module
```

## 模板
- [[templates/bug-simple|轻量级模板]] - 日常小问题，3 分钟完成
- [[templates/bug-detailed|详细模板]] - 架构性问题，含根因分析
