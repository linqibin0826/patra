---
description: 生成周报（汇总本周每日日志）
argument-hint: [周数 | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**：

| 参数格式 | 含义 | 示例 |
|----------|------|------|
| 空 | 本周 | `/devlog-week` |
| `YYYY-Www` | 指定周 | `/devlog-week 2025-W47` |

---

## 执行流程

### 第一步：解析参数

```
用户参数: $ARGUMENTS
```

1. 如果参数为空 → 使用当前周（ISO 8601 周数格式）
2. 如果参数是周数 → 使用指定周

计算周的日期范围（周一到周日）。

### 第二步：读取每日日志

1. 查找 `../Patra-docs/content/devlog/daily/` 目录下该周的所有日志文件
2. 解析每个日志的 frontmatter 提取统计数据
3. 如果某天没有日志，统计为 0

### 第三步：汇总统计

| 指标 | 计算方式 |
|------|----------|
| 工作日数 | 有日志的天数 |
| 总提交数 | sum(commits) |
| 总文件数 | sum(files_changed) |
| 新增行数 | sum(lines_added) |
| 删除行数 | sum(lines_deleted) |

### 第四步：提取亮点

从每日日志的「今日完成」部分提取本周主要成就：

1. 新功能开发
2. 重要 Bug 修复
3. 架构重构
4. 性能优化

### 第五步：模块变更分布

统计各模块的变更情况：

```
patra-catalog: 15 commits, 234 files
patra-ingest: 8 commits, 89 files
...
```

### 第六步：生成周报文件

**文件路径**: `../Patra-docs/content/devlog/weekly/{YYYY-Www}.md`

**文件内容**：

```markdown
---
week: {YYYY-Www}
type: devlog/weekly
date_range: "{START_DATE} ~ {END_DATE}"
total_commits: {TOTAL_COMMITS}
total_files: {TOTAL_FILES}
highlights: [{HIGHLIGHTS}]
tags: [sprint-review]
---

# {YYYY-Www} 周报

## 本周统计

| 指标 | 数值 |
|------|------|
| 工作日数 | {WORKING_DAYS} |
| 总提交数 | {TOTAL_COMMITS} |
| 修改文件 | {TOTAL_FILES} |
| 新增行数 | +{TOTAL_ADDED} |
| 删除行数 | -{TOTAL_DELETED} |

## 每日概览

```dataview
TABLE WITHOUT ID
  file.link as "日期",
  commits as "提交",
  files_changed as "文件",
  join(modules, ", ") as "模块"
FROM "devlog/daily"
WHERE dateformat(date, "yyyy-'W'WW") = "{YYYY-Www}"
SORT date ASC
```

## 本周亮点

{HIGHLIGHTS_CONTENT}

## 模块变更分布

{MODULE_DISTRIBUTION}

## 关联记录

### 本周 Bug

```dataview
LIST
FROM "bugs"
WHERE date >= date("{START_DATE}") AND date <= date("{END_DATE}")
SORT date ASC
```

### 本周 TIL

```dataview
LIST
FROM "til"
WHERE date >= date("{START_DATE}") AND date <= date("{END_DATE}")
SORT date ASC
```

### 本周 ADR

```dataview
LIST
FROM "adr" OR "decisions"
WHERE date >= date("{START_DATE}") AND date <= date("{END_DATE}")
SORT date ASC
```

## 下周计划

{NEXT_WEEK_PLAN}
```

### 第七步：生成下周计划

基于以下信息生成下周计划建议：

1. **本周未完成**：从每日日志的「明日计划」中提取未勾选的任务
2. **代码中的 TODO**：扫描本周修改的文件中的 TODO/FIXME
3. **常规建议**：如测试覆盖、文档完善等

---

## 输出格式

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 周报已生成
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📅 周数：{YYYY-Www}
📆 日期范围：{START_DATE} ~ {END_DATE}

📈 本周统计：
  • 工作日数：{WORKING_DAYS}
  • 总提交数：{TOTAL_COMMITS}
  • 修改文件：{TOTAL_FILES}
  • 新增/删除：+{TOTAL_ADDED} / -{TOTAL_DELETED}

🏆 本周亮点：
{HIGHLIGHTS_PREVIEW}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📁 文件：../Patra-docs/content/devlog/weekly/{YYYY-Www}.md
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 使用示例

```bash
# 生成本周周报
/devlog-week

# 生成指定周的周报
/devlog-week 2025-W47
```

---

## 注意事项

1. **依赖每日日志**：需要先有每日日志才能生成周报
2. **增量更新**：如果周报已存在，提示用户选择覆盖或取消
3. **Dataview 查询**：周报中的 Dataview 查询会动态显示数据
4. **亮点提取**：从 commit message 中识别 feat/fix 等重要变更
