---
type: devlog
period: monthly
month: {{month}}
total_commits: {{total_commits}}
total_files: {{total_files}}
milestones: []
tags:
  - record/devlog
  - period/monthly
  - review/monthly
---

# {{month}} 月报

## 本月统计

| 指标 | 数值 |
|------|------|
| 工作日数 | {{working_days}} |
| 总提交数 | {{total_commits}} |
| 修改文件 | {{total_files}} |
| 新增行数 | +{{total_added}} |
| 删除行数 | -{{total_deleted}} |

## 周报汇总

```dataview
TABLE WITHOUT ID
  file.link as "周",
  date_range as "日期范围",
  total_commits as "提交",
  total_files as "文件"
FROM "devlog/weekly"
WHERE contains(week, "{{month_prefix}}")
SORT week ASC
```

## 每日提交趋势

```dataview
TABLE WITHOUT ID
  file.link as "日期",
  commits as "提交",
  files_changed as "文件"
FROM "devlog/daily"
WHERE dateformat(date, "yyyy-MM") = "{{month}}"
SORT date ASC
```

## 里程碑

{{milestones}}

## 模块变更分布

{{module_distribution}}

## 关联记录

<!-- 手动添加链接时使用 Wikilink 语法：[[bugs/2025/11/BUG-001-xxx|BUG-001-xxx]] -->

### 本月 Bug

```dataview
LIST
FROM "bugs"
WHERE dateformat(date, "yyyy-MM") = "{{month}}"
SORT date ASC
```

### 本月 TIL

```dataview
LIST
FROM "til"
WHERE dateformat(date, "yyyy-MM") = "{{month}}"
SORT date ASC
```

### 本月 ADR

```dataview
LIST
FROM "adr" OR "decisions"
WHERE dateformat(date, "yyyy-MM") = "{{month}}"
SORT date ASC
```

## 下月计划

{{next_month_plan}}
