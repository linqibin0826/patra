---
description: 生成每日开发日志（基于 git 变更）
argument-hint: [时间范围 | 日期 | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**：

| 参数格式 | 含义 | 示例 |
|----------|------|------|
| 空 | 自动判断工作日（见下方说明） | `/devlog` |
| `HH:MM-HH:MM` | 今日指定时间范围 | `/devlog 08:00-23:00` |
| `YYYY-MM-DD` | 指定日期全天 | `/devlog 2025-11-26` |
| `YYYY-MM-DD HH:MM-HH:MM` | 指定日期和时间范围 | `/devlog 2025-11-26 08:00-23:00` |

**凌晨时间智能处理**：

当参数为空时，根据当前时间自动判断记录哪一天的工作：

| 当前时间 | 记录日期 | 时间范围 | 说明 |
|----------|----------|----------|------|
| 06:00 ~ 23:59 | 今天 | 06:00 → 当前时间 | 正常工作时间 |
| 00:00 ~ 05:59 | **昨天** | 昨天 06:00 → 今天当前时间 | 跨夜工作，记录为昨天的日志 |

**示例**：
- 现在是 `2025-11-27 23:30`，执行 `/devlog` → 记录 `2025-11-27` 的日志，时间范围 `06:00 - 23:30`
- 现在是 `2025-11-28 01:30`，执行 `/devlog` → 记录 `2025-11-27` 的日志，时间范围 `06:00 - 次日01:30`

---

## 执行流程

### 第一步：解析参数和判断日期

```
用户参数: $ARGUMENTS
当前时间: $(date +%H:%M)
```

**日期判断逻辑**：

```
如果参数为空：
    如果当前时间 >= 06:00：
        记录日期 = 今天
        时间范围 = 今天 06:00 → 当前时间
    否则（凌晨 00:00 ~ 05:59）：
        记录日期 = 昨天
        时间范围 = 昨天 06:00 → 今天当前时间（跨夜）
如果参数是时间范围：
    记录日期 = 今天
    时间范围 = 用户指定
如果参数是日期：
    记录日期 = 用户指定
    时间范围 = 06:00 → 23:59
如果参数是日期 + 时间范围：
    记录日期 = 用户指定日期
    时间范围 = 用户指定时间范围
```

### 第二步：获取 Git 变更

**分步执行 git 命令**（避免嵌套命令导致的解析错误）：

```bash
# 1. 获取提交列表（带统计信息）
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --pretty=format:"%h|%s|%cd" --date=format:"%H:%M" --stat

# 2. 获取提交数量
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --oneline | wc -l

# 3. 获取变更统计（简化方式：统计当天所有提交的变更）
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --shortstat --oneline

# 4. 获取修改的文件列表
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --name-only --pretty=format:"" | sort -u | grep -v "^$"
```

**注意**：不要使用嵌套的 `$(...)` 子命令，容易导致 shell 解析错误。分步获取数据后再汇总。

### 第三步：分析变更

1. **统计变更**：
   - 提交数量
   - 修改文件数
   - 新增行数
   - 删除行数

2. **按模块分组**：
   根据文件路径识别所属模块（patra-catalog、patra-ingest 等）

3. **提取标签**：
   从 commit message 中提取关键词作为标签（如 feat、fix、refactor、mesh、xml 等）

4. **检测关联**：
   - 检查是否有当天创建的 Bug 记录
   - 检查是否有当天创建的 TIL 记录
   - 检查是否有当天创建的 ADR 记录

### 第四步：生成今日完成摘要

基于 commit message 分析今日完成的工作：

1. 按类型分组（feature、bugfix、refactor、docs 等）
2. 提取主要工作内容
3. 生成简洁的完成摘要

### 第五步：AI 建议明日计划

基于以下信息生成明日计划建议：

1. **今日变更分析**：
   - 是否有未完成的重构
   - 是否有待补充的测试
   - 是否有需要完善的文档

2. **代码中的 TODO/FIXME**：
   先获取修改的文件列表，再用 Grep 工具搜索 TODO/FIXME（避免使用嵌套 shell 命令）

3. **常见后续工作**：
   - 新功能 → 建议补充测试
   - 重构 → 建议验证兼容性
   - Bug 修复 → 建议添加回归测试

### 第六步：生成日志文件

**文件路径**: `docs/devlog/daily/{YYYY-MM-DD}.md`

**文件内容**：

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

> 以下是基于今日变更和未完成任务的建议，请确认或修改：

{SUGGESTIONS}

## 关联

- Bug: {BUG_LINKS}
- TIL: {TIL_LINKS}
- ADR: {ADR_LINKS}

## 备注

<!-- 可添加个人思考、遇到的问题、心得体会等 -->
```

### 第七步：征求用户确认

展示生成的日志内容，特别是「明日计划」部分，询问用户：

1. 是否需要修改明日计划
2. 是否需要添加备注
3. 是否确认保存

---

## 输出格式

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📝 开发日志已生成
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📅 日期：{YYYY-MM-DD}
⏰ 时间范围：{TIME_START} - {TIME_END}

📊 变更统计：
  • 提交数：{COMMIT_COUNT}
  • 修改文件：{FILES_COUNT}
  • 新增/删除：+{LINES_ADDED} / -{LINES_DELETED}
  • 涉及模块：{MODULES}

🎯 明日计划建议：
{SUGGESTIONS_PREVIEW}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📁 文件：docs/devlog/daily/{YYYY-MM-DD}.md
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

是否需要修改明日计划？(确认/修改/取消)
```

---

## 使用示例

```bash
# 生成今日开发日志（默认从早上6点到当前时间）
/devlog

# 指定今日时间范围
/devlog 08:00-23:00

# 生成昨天的日志
/devlog 2025-11-26

# 指定日期和时间范围
/devlog 2025-11-26 09:00-18:00
```

---

## 注意事项

1. **跨夜工作处理**：凌晨 00:00~05:59 执行时，自动记录为「昨天」的日志（详见参数说明）
2. **增量更新**：如果当天日志已存在，提示用户选择覆盖或追加
3. **关联检测**：自动检测当天是否有 Bug/TIL/ADR 记录，自动添加双向链接
4. **模块识别**：根据文件路径自动识别 patra-* 模块
5. **标签提取**：从 commit message 前缀（feat/fix/refactor/docs/test）提取类型标签
6. **避免嵌套命令**：执行 git 命令时，不要使用 `$(...)` 嵌套子命令，分步执行后汇总数据
