---
title: 学习笔记
type: moc
updated: 2025-11-27
---

# 学习笔记 (TIL)

> Today I Learned - 记录每天学到的新知识点

## 按分类

### Spring

```dataview
LIST
FROM "til/spring"
SORT date DESC
```

### MyBatis

```dataview
LIST
FROM "til/mybatis"
SORT date DESC
```

### Java

```dataview
LIST
FROM "til/java"
SORT date DESC
```

### 架构

```dataview
LIST
FROM "til/architecture"
SORT date DESC
```

### AI 编程

```dataview
LIST
FROM "til/ai-coding"
SORT date DESC
```

## 最近 30 天

```dataview
TABLE category, confidence, source
FROM "til"
WHERE date >= date(today) - dur(30 days)
SORT date DESC
```

## 高置信度知识

```dataview
LIST
FROM "til"
WHERE confidence = "high"
SORT date DESC
```

## 统计

```dataview
TABLE length(rows) as "笔记数量"
FROM "til"
WHERE file.name != "_MOC"
GROUP BY category
```

## 模板
- [[templates/til|TIL 模板]] - 新知识点记录
