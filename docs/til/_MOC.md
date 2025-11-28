---
title: 每日学习总结
type: moc
updated: 2025-11-28
---

# 每日学习总结 (TIL)

> Today I Learned - 每天学习结束后的知识汇总

## 按时间浏览

### 2025 年

```dataview
TABLE WITHOUT ID
  link(file.path, dateformat(date, "MM-dd")) as "日期",
  file.name as "标题",
  join(tags, ", ") as "标签"
FROM "til/2025"
SORT date DESC
```

## 最近 7 天

```dataview
TABLE WITHOUT ID
  link(file.path, dateformat(date, "MM-dd")) as "日期",
  file.name as "主题",
  learning_series as "学习系列"
FROM "til"
WHERE date >= date(today) - dur(7 days) AND file.name != "_MOC"
SORT date DESC
```

## 最近 30 天

```dataview
TABLE WITHOUT ID
  dateformat(date, "yyyy-MM-dd") as "日期",
  file.link as "主题",
  join(tags, ", ") as "标签"
FROM "til"
WHERE date >= date(today) - dur(30 days) AND file.name != "_MOC"
SORT date DESC
```

## 按学习系列分组

```dataview
TABLE WITHOUT ID
  learning_series as "系列",
  length(rows) as "天数",
  sum(rows.chapters_completed) as "完成章节"
FROM "til"
WHERE file.name != "_MOC" AND learning_series
GROUP BY learning_series
```

## 按标签统计

```dataview
TABLE WITHOUT ID
  tags as "标签",
  length(rows) as "笔记数"
FROM "til"
WHERE file.name != "_MOC"
FLATTEN tags
GROUP BY tags
SORT length(rows) DESC
LIMIT 10
```

## 统计

```dataview
TABLE WITHOUT ID
  dateformat(date, "yyyy-MM") as "月份",
  length(rows) as "学习天数"
FROM "til"
WHERE file.name != "_MOC"
GROUP BY dateformat(date, "yyyy-MM")
SORT rows[0].date DESC
```

## 相关链接

- [[learning/_index|学习材料索引]] - 详细的学习教程
- [[decisions/_index|架构决策索引]] - ADR 记录
