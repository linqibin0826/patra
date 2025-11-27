# docs 目录使用指南

本目录是项目的知识管理中心，使用 Obsidian 管理，记录开发日志、Bug、学习笔记和架构决策。

## 目录结构

```
docs/
├── devlog/         # 开发日志
│   ├── daily/      # 每日日志
│   ├── weekly/     # 周报
│   └── monthly/    # 月报
├── bugs/           # Bug 记录
├── til/            # Today I Learned 学习笔记
├── decisions/      # ADR 架构决策记录
├── templates/      # 模板文件（参考用，不要修改）
└── _MOC.md         # 主索引
```

## 何时记录

### 开发日志 (`devlog/`)

**触发条件**：
- 每天开发结束时（晚上或凌晨）
- 用户执行 `/devlog` 命令

**使用命令**：
```bash
# 生成今日开发日志（默认从早上6点到当前时间）
/devlog

# 指定时间范围
/devlog 08:00-23:00

# 生成指定日期的日志
/devlog 2025-11-26

# 生成周报
/devlog-week

# 生成月报
/devlog-month
```

**命名规范**：
- 每日日志：`devlog/daily/YYYY-MM-DD.md`
- 周报：`devlog/weekly/YYYY-Www.md`
- 月报：`devlog/monthly/YYYY-MM.md`

**模板**：
- `templates/devlog-daily.md`
- `templates/devlog-weekly.md`
- `templates/devlog-monthly.md`

### Bug 记录 (`bugs/`)

**触发条件**：
- 修复了一个非平凡的 Bug（需要调试超过 10 分钟）
- 发现了一个值得记录的坑或陷阱
- 用户明确要求记录

**命名规范**：`bugs/YYYY/MM/BUG-NNN-简短描述.md`

**示例**：`bugs/2025/11/BUG-001-transaction-not-rollback.md`

**模板选择**：
- 日常小问题 → `templates/bug-simple.md`
- 架构性问题（需要根因分析）→ `templates/bug-detailed.md`

### TIL 学习笔记 (`til/`)

**触发条件**：
- 解决问题时学到了新知识
- 发现了一个有用的技巧或最佳实践
- 用户明确要求记录

**目录分类**：
- `til/spring/` - Spring 相关
- `til/mybatis/` - MyBatis 相关
- `til/java/` - Java 语言特性
- `til/architecture/` - 架构设计
- `til/ai-coding/` - AI 编程技巧

**命名规范**：`til/{category}/YYYY-MM-DD-简短描述.md`

**示例**：`til/spring/2025-11-27-conditional-annotation-priority.md`

**模板**：`templates/til.md`

### ADR 架构决策 (`decisions/`)

**触发条件**：
- 做出了重要的技术选型决策
- 改变了现有架构模式
- 用户明确要求记录

**命名规范**：`decisions/ADR-NNN-简短描述.md`

**示例**：`decisions/ADR-004-choose-redisson-for-distributed-lock.md`

**模板**：`templates/adr.md`

## Frontmatter 格式

所有文档必须包含 YAML frontmatter，格式如下：

### 开发日志（每日）

```yaml
---
date: 2025-11-27
type: devlog/daily
time_range: "06:00 - 23:30"
commits: 5
files_changed: 12
lines_added: 234
lines_deleted: 89
modules: [patra-catalog, patra-ingest]
tags: [mesh-import, xml-parser, refactoring]
linked_bugs: []
linked_tils: []
linked_adrs: []
---
```

### 开发日志（周报）

```yaml
---
week: 2025-W48
type: devlog/weekly
date_range: "2025-11-25 ~ 2025-11-30"
total_commits: 23
total_files: 45
highlights: []
tags: [sprint-review]
---
```

### 开发日志（月报）

```yaml
---
month: 2025-11
type: devlog/monthly
total_commits: 87
total_files: 156
milestones: []
tags: [monthly-review]
---
```

### Bug 文档

```yaml
---
id: BUG-2025-001
date: 2025-11-27
severity: low | medium | high | critical
status: open | fixed | wontfix
tags: [mybatis, spring-boot]
module: patra-ingest
resolved_at: 2025-11-28  # 仅 fixed 状态需要
time_spent: 2h           # 可选
---
```

### TIL 文档

```yaml
---
date: 2025-11-27
category: spring | mybatis | java | architecture | ai-coding
tags: [auto-config, conditional]
source: debugging | reading | experiment | ai-suggestion
confidence: low | medium | high
---
```

### ADR 文档

```yaml
---
id: ADR-001
date: 2025-11-27
status: proposed | accepted | deprecated | superseded
tags: [architecture, decision]
---
```

## 双向链接

使用 `[[]]` 语法创建文档间的链接：

```markdown
- 开发日志: [[devlog/daily/2025-11-27]]
- 周报: [[devlog/weekly/2025-W48]]
- 相关 Bug: [[bugs/2025/11/BUG-001-xxx]]
- 延伸阅读: [[til/spring/2025-11-27-xxx]]
- 架构决策: [[decisions/ADR-001-xxx]]
```

## 注意事项

1. **不要修改 `templates/` 目录下的文件**，它们是模板参考
2. **不要修改 `_MOC.md` 文件**，它们包含 Dataview 查询，会自动生成索引
3. **使用中文编写内容**，但文件名使用英文
4. **记录要简洁**，重点是「学到了什么」和「如何解决」
5. **及时记录**，趁记忆清晰时写下来
