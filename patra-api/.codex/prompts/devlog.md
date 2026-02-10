---
description: 生成每日开发日志（基于 Git 变更，Codex 版）
argument-hint: [时间范围 | 日期 | 空]
---

你将根据本地 Git 变更生成一份开发日志，并写入 `../Patra-docs/content/devlog/daily/`。写入前必须先展示草稿并征求确认。

## 参数解析

用户参数：`$ARGUMENTS`

支持格式：

| 参数格式 | 含义 | 示例 |
|----------|------|------|
| 空 | 自动判断工作日 | `/prompts:devlog` |
| `HH:MM-HH:MM` | 今日指定时间范围 | `/prompts:devlog 08:00-23:00` |
| `YYYY-MM-DD` | 指定日期全天 | `/prompts:devlog 2026-02-09` |
| `YYYY-MM-DD HH:MM-HH:MM` | 指定日期+时间范围 | `/prompts:devlog 2026-02-09 08:00-23:00` |

## 凌晨时间智能处理（参数为空时）

| 当前时间 | 记录日期 | 时间范围 |
|------|------|------|
| 06:00 ~ 23:59 | 今天 | 今天 06:00 → 当前时间 |
| 00:00 ~ 05:59 | 昨天 | 昨天 06:00 → 今天当前时间（跨夜） |

## 第一步：获取 Git 变更（分步执行，禁止嵌套子命令）

1. 提交列表与统计：

```bash
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --pretty=format:"%h|%s|%cd" --date=format:"%H:%M" --stat
```

2. 提交数量：

```bash
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --oneline | wc -l
```

3. 变更统计：

```bash
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --shortstat --oneline
```

4. 修改文件列表：

```bash
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --name-only --pretty=format:"" | sort -u | grep -v "^$"
```

## 第二步：分析变更

1. 汇总指标：提交数、修改文件数、新增行、删除行
2. 按模块分组：`patra-ingest`、`patra-registry` 等
3. 提取标签：`feat`、`fix`、`refactor`、`docs`、`test` 等
4. 检测关联：
   - `../Patra-docs/content/bugs/`
   - `../Patra-docs/content/til/`
   - `../Patra-docs/content/decisions/`

## 第三步：生成日志草稿

目标文件：`../Patra-docs/content/devlog/daily/{YYYY-MM-DD}.md`

模板：

```markdown
---
date: {YYYY-MM-DD}
type: devlog/daily
time_range: "{TIME_START} - {TIME_END}"
commits: {COMMIT_COUNT}
files_changed: {FILES_COUNT}
lines_added: {LINES_ADDED}
lines_deleted: {LINES_DELETED}
modules: [{MODULES}]
tags: [{TAGS}]
linked_bugs: [{BUG_LINKS}]
linked_tils: [{TIL_LINKS}]
linked_adrs: [{ADR_LINKS}]
---

# {YYYY-MM-DD} 开发日志

## 变更统计

| 指标 | 数值 |
|------|------|
| 提交数 | {COMMIT_COUNT} |
| 修改文件 | {FILES_COUNT} |
| 新增行数 | +{LINES_ADDED} |
| 删除行数 | -{LINES_DELETED} |
| 涉及模块 | {MODULES} |

## 提交记录

{COMMITS_BY_MODULE}

## 今日完成

{COMPLETED_SUMMARY}

## 明日计划

> 以下为 AI 建议，请确认或修改：

{SUGGESTIONS}

## 关联

- Bug: {BUG_LINKS}
- TIL: {TIL_LINKS}
- ADR: {ADR_LINKS}

## 备注

_待补充_
```

## 第四步：征求确认后写入

写入前必须询问：

1. 是否修改“明日计划”
2. 是否追加备注
3. 是否确认保存

确认后再执行写入。如果文件已存在，先询问“覆盖”还是“追加”。

