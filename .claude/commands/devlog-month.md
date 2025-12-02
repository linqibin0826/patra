---
description: 生成月报（汇总本月周报和每日日志）
argument-hint: [月份 | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**：

| 参数格式 | 含义 | 示例 |
|----------|------|------|
| 空 | 本月 | `/devlog-month` |
| `YYYY-MM` | 指定月 | `/devlog-month 2025-10` |

---

## 执行流程

### 第一步：解析参数

```
用户参数: $ARGUMENTS
```

1. 如果参数为空 → 使用当前月份
2. 如果参数是月份 → 使用指定月份

### 第二步：读取周报和每日日志

1. 查找 `../Patra-docs/content/devlog/weekly/` 目录下该月的所有周报
2. 查找 `../Patra-docs/content/devlog/daily/` 目录下该月的所有日志
3. 解析 frontmatter 提取统计数据

### 第三步：汇总统计

| 指标 | 计算方式 |
|------|----------|
| 工作日数 | 有日志的天数 |
| 总提交数 | sum(commits) from daily logs |
| 总文件数 | sum(files_changed) from daily logs |
| 新增行数 | sum(lines_added) |
| 删除行数 | sum(lines_deleted) |

### 第四步：提取里程碑

从本月的变更中提取重要里程碑：

1. **功能完成**：重大功能的完成
2. **版本发布**：如有版本发布记录
3. **架构变更**：重要的架构决策
4. **技术债务**：解决的技术债务

### 第五步：模块变更分布

统计各模块在本月的变更情况，包括：
- 提交数量
- 文件变更数
- 代码增删行数

### 第六步：生成月报文件

**文件路径**: `../Patra-docs/content/devlog/monthly/{YYYY-MM}.md`

**文件内容**：

```markdown
---
month: {YYYY-MM}
type: devlog/monthly
total_commits: {TOTAL_COMMITS}
total_files: {TOTAL_FILES}
milestones: [{MILESTONES}]
tags: [monthly-review]
---

# {YYYY-MM} 月报

## 本月统计

| 指标 | 数值 |
|------|------|
| 工作日数 | {WORKING_DAYS} |
| 总提交数 | {TOTAL_COMMITS} |
| 修改文件 | {TOTAL_FILES} |
| 新增行数 | +{TOTAL_ADDED} |
| 删除行数 | -{TOTAL_DELETED} |

## 周报汇总

```dataview
TABLE WITHOUT ID
  file.link as "周",
  date_range as "日期范围",
  total_commits as "提交",
  total_files as "文件"
FROM "devlog/weekly"
WHERE contains(week, "{YYYY}")
SORT week ASC
```

## 每日提交趋势

```dataview
TABLE WITHOUT ID
  file.link as "日期",
  commits as "提交",
  files_changed as "文件"
FROM "devlog/daily"
WHERE dateformat(date, "yyyy-MM") = "{YYYY-MM}"
SORT date ASC
```

## 里程碑

{MILESTONES_CONTENT}

## 模块变更分布

{MODULE_DISTRIBUTION}

## 关联记录

### 本月 Bug

```dataview
LIST
FROM "bugs"
WHERE dateformat(date, "yyyy-MM") = "{YYYY-MM}"
SORT date ASC
```

### 本月 TIL

```dataview
LIST
FROM "til"
WHERE dateformat(date, "yyyy-MM") = "{YYYY-MM}"
SORT date ASC
```

### 本月 ADR

```dataview
LIST
FROM "adr" OR "decisions"
WHERE dateformat(date, "yyyy-MM") = "{YYYY-MM}"
SORT date ASC
```

## 下月计划

{NEXT_MONTH_PLAN}
```

### 第七步：生成下月计划

基于以下信息生成下月计划建议：

1. **本月未完成**：从周报的「下周计划」中提取未完成的任务
2. **长期目标**：如有项目路线图，对齐下月目标
3. **技术债务**：累积的 TODO/FIXME

---

## 输出格式

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 月报已生成
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📅 月份：{YYYY-MM}

📈 本月统计：
  • 工作日数：{WORKING_DAYS}
  • 总提交数：{TOTAL_COMMITS}
  • 修改文件：{TOTAL_FILES}
  • 新增/删除：+{TOTAL_ADDED} / -{TOTAL_DELETED}

🏆 里程碑：
{MILESTONES_PREVIEW}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📁 文件：../Patra-docs/content/devlog/monthly/{YYYY-MM}.md
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 使用示例

```bash
# 生成本月月报
/devlog-month

# 生成指定月份的月报
/devlog-month 2025-10
```

---

## 注意事项

1. **依赖日志数据**：需要先有每日日志和周报才能生成完整月报
2. **增量更新**：如果月报已存在，提示用户选择覆盖或取消
3. **Dataview 查询**：月报中的 Dataview 查询会动态显示数据
4. **里程碑识别**：从 commit message 和 ADR 中识别重要里程碑
