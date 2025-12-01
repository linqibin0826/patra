---
title: 架构决策记录
type: moc
updated: 2025-12-01
---

# 架构决策记录 (ADR)

> Architecture Decision Records - 记录重要的架构决策及其背景

## 按状态

### 已接受

```dataview
TABLE date, tags
FROM "decisions"
WHERE status = "accepted"
SORT id ASC
```

### 待讨论

```dataview
TABLE date, tags
FROM "decisions"
WHERE status = "proposed"
SORT date DESC
```

### 已废弃

```dataview
TABLE date, tags
FROM "decisions"
WHERE status = "deprecated" OR status = "superseded"
SORT date DESC
```

## 所有决策

```dataview
TABLE status, date
FROM "decisions"
WHERE file.name != "_MOC"
SORT id ASC
```

## 关联设计文档

ADR 记录"为什么选择这个方案"，Design 记录"这个功能/模块的架构是什么"。

```dataview
TABLE related_designs as "关联设计", status as "状态"
FROM "decisions"
WHERE type = "adr" AND length(related_designs) > 0
SORT adr_id ASC
```

- [[designs/_MOC|设计文档索引]] - 功能/模块架构设计

## 模板
- [[templates/adr|ADR 模板]] - 架构决策记录

## 参考资料
- [ADR GitHub](https://adr.github.io/)
- [Michael Nygard's ADR Template](https://github.com/joelparkerhenderson/architecture-decision-record)
