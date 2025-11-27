---
description: 生成每日开发日志（基于 git 变更）
argument-hint: [时间范围 | 日期 | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**：

| 参数格式 | 含义 | 示例 |
|----------|------|------|
| 空 | 今日 06:00 到当前时间 | `/devlog` |
| `HH:MM-HH:MM` | 今日指定时间范围 | `/devlog 08:00-23:00` |
| `YYYY-MM-DD` | 指定日期全天 | `/devlog 2025-11-26` |
| `YYYY-MM-DD HH:MM-HH:MM` | 指定日期和时间范围 | `/devlog 2025-11-26 08:00-23:00` |

---

## 执行流程

### 第一步：解析参数

```
用户参数: $ARGUMENTS
```

1. 如果参数为空 → 使用今日日期，时间范围 06:00 到当前时间
2. 如果参数是时间范围 → 使用今日日期 + 指定时间范围
3. 如果参数是日期 → 使用指定日期，时间范围 06:00 到 23:59
4. 如果参数是日期 + 时间范围 → 使用指定日期和时间范围

### 第二步：获取 Git 变更

执行以下 git 命令获取指定时间范围内的提交：

```bash
# 获取提交列表（带统计信息）
git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --pretty=format:"%h|%s|%cd" --date=format:"%H:%M" --stat

# 获取总体统计
git diff --stat $(git log --after="{DATE} {TIME_START}" --before="{DATE} {TIME_END}" \
  --pretty=format:"%H" | tail -1)^..$(git log --after="{DATE} {TIME_START}" \
  --before="{DATE} {TIME_END}" --pretty=format:"%H" | head -1)
```

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
   ```bash
   git diff --name-only HEAD~{COMMIT_COUNT}..HEAD | \
     xargs grep -l "TODO\|FIXME" 2>/dev/null
   ```

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

1. **智能时间处理**：如果当前时间早于 06:00（如凌晨 1 点），自动判断为记录前一天的日志
2. **增量更新**：如果当天日志已存在，提示用户选择覆盖或追加
3. **关联检测**：自动检测当天是否有 Bug/TIL/ADR 记录，自动添加双向链接
4. **模块识别**：根据文件路径自动识别 patra-* 模块
5. **标签提取**：从 commit message 前缀（feat/fix/refactor/docs/test）提取类型标签
