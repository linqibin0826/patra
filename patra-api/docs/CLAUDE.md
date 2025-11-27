# docs 目录使用指南

本目录是项目的知识管理中心，使用 Obsidian 管理，记录 Bug、学习笔记和架构决策。

## 目录结构

```
docs/
├── bugs/           # Bug 记录
├── til/            # Today I Learned 学习笔记
├── decisions/      # ADR 架构决策记录
├── templates/      # 模板文件（参考用，不要修改）
└── _MOC.md         # 主索引
```

## 何时记录

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
