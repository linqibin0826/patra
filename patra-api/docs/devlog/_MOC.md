---
title: 开发日志
type: moc
updated: 2025-11-28
---

# 开发日志

## 快速导航

- [[devlog/daily/|每日日志]]
- [[devlog/weekly/|周报]]
- [[devlog/monthly/|月报]]

## 总览

```dataview
TABLE WITHOUT ID
  dateformat(date, "yyyy-MM") as "月份",
  sum(rows.commits) as "提交数",
  sum(rows.files_changed) as "文件数",
  length(rows) as "日志数"
FROM "devlog/daily"
GROUP BY dateformat(date, "yyyy-MM")
SORT rows[0].date DESC
```

## 最近日志

```dataview
TABLE WITHOUT ID
  file.link as "日期",
  commits as "提交",
  files_changed as "文件",
  join(modules, ", ") as "模块"
FROM "devlog/daily"
SORT date DESC
LIMIT 7
```

## 周报

```dataview
TABLE WITHOUT ID
  file.link as "周",
  date_range as "日期范围",
  total_commits as "提交",
  join(highlights, ", ") as "亮点"
FROM "devlog/weekly"
SORT week DESC
LIMIT 4
```

## 月报

```dataview
TABLE WITHOUT ID
  file.link as "月份",
  total_commits as "提交",
  total_files as "文件",
  join(milestones, ", ") as "里程碑"
FROM "devlog/monthly"
SORT month DESC
LIMIT 3
```

## 标签统计

```dataview
TABLE WITHOUT ID
  tags as "标签",
  length(rows) as "出现次数"
FROM "devlog/daily"
FLATTEN tags
GROUP BY tags
SORT length(rows) DESC
LIMIT 10
```

## 模块活跃度

```dataview
TABLE WITHOUT ID
  modules as "模块",
  length(rows) as "日志数",
  sum(rows.commits) as "总提交"
FROM "devlog/daily"
FLATTEN modules
GROUP BY modules
SORT sum(rows.commits) DESC
```
