---
type: devlog
period: weekly
week: {{week}}
date_range: "{{start_date}} ~ {{end_date}}"
total_commits: {{total_commits}}
total_files: {{total_files}}
highlights: []
tags:
  - record/devlog
  - period/weekly
  - review/sprint
---

# {{week}} 周报

## 本周统计

| 指标 | 数值 |
|------|------|
| 工作日数 | {{working_days}} |
| 总提交数 | {{total_commits}} |
| 修改文件 | {{total_files}} |
| 新增行数 | +{{total_added}} |
| 删除行数 | -{{total_deleted}} |

## 每日概览

```dataview
TABLE WITHOUT ID
  file.link as "日期",
  commits as "提交",
  files_changed as "文件",
  join(modules, ", ") as "模块"
FROM "devlog/daily"
WHERE dateformat(date, "yyyy-'W'WW") = "{{week}}"
SORT date ASC
```

## 本周亮点

{{highlights}}

## 模块变更分布

{{module_distribution}}

## 关联记录

<!-- 手动添加链接时使用 Wikilink 语法：[[bugs/2025/11/BUG-001-xxx|BUG-001-xxx]] -->

### 本周 Bug

```dataview
LIST
FROM "bugs"
WHERE date >= date("{{start_date}}") AND date <= date("{{end_date}}")
SORT date ASC
```

### 本周 TIL

```dataview
LIST
FROM "til"
WHERE date >= date("{{start_date}}") AND date <= date("{{end_date}}")
SORT date ASC
```

### 本周 ADR

```dataview
LIST
FROM "adr" OR "decisions"
WHERE date >= date("{{start_date}}") AND date <= date("{{end_date}}")
SORT date ASC
```

## 下周计划

{{next_week_plan}}
