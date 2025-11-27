---
title: Patra 文档中心
updated: 2025-11-27
---

# Patra 文档中心

## 快速导航

- [[bugs/_MOC|Bug 记录]]
- [[til/_MOC|学习笔记 (TIL)]]
- [[decisions/_MOC|架构决策 (ADR)]]

## 最近更新

```dataview
TABLE file.mtime as "更新时间", tags
FROM ""
WHERE file.name != "_MOC"
SORT file.mtime DESC
LIMIT 10
```

## 未解决的 Bug

```dataview
TABLE severity, date
FROM "bugs"
WHERE status = "open"
SORT severity DESC
```

## 本周学习

```dataview
LIST
FROM "til"
WHERE date >= date(today) - dur(7 days)
SORT date DESC
```
