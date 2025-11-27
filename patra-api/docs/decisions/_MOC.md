---
title: 架构决策记录
type: moc
updated: 2025-11-27
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

## 模板
- [[templates/adr|ADR 模板]] - 架构决策记录

## 参考资料
- [ADR GitHub](https://adr.github.io/)
- [Michael Nygard's ADR Template](https://github.com/joelparkerhenderson/architecture-decision-record)
