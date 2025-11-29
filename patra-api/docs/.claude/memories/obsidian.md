# Obsidian 语法与元数据规范

## 链接语法

**内部文档**使用 Wikilink 语法 `[[文件名]]`，**外部链接**使用 Markdown 语法 `[文本](URL)`。

## 任务列表

使用 Markdown 复选框语法，**禁止使用 emoji**（`✅ ⬜ 🔲`）：

| 状态 | 语法 | 状态 | 语法 |
|------|------|------|------|
| 未完成 | `- [ ] 任务` | 推迟 | `- [>] 任务` |
| 已完成 | `- [x] 任务` | 待确认 | `- [?] 任务` |
| 重要 | `- [!] 任务` | 取消 | `- [-] 任务` |

## Callouts 提示框

使用 Obsidian 原生 Callout 语法，**禁止使用 emoji 模拟**：

```markdown
> [!note] 标题（可选）
> 内容
```

常用类型：`[!note]` `[!tip]` `[!important]` `[!warning]` `[!danger]` `[!example]` `[!info]`

折叠语法：`[!note]+`（默认展开）或 `[!note]-`（默认折叠）

## 嵌入与引用

| 用途 | 语法 |
|------|------|
| 嵌入整个文件 | `![[文件名]]` |
| 嵌入标题段落 | `![[文件名#标题]]` |
| 嵌入块 | `![[文件名#^block-id]]` |
| 嵌入图片 | `![[image.png]]` |
| 调整图片大小 | `![[image.png\|300]]` |

块引用：在段落末尾添加 `^block-id`，通过 `[[文件名#^important-block]]` 引用。

## 其他格式

- **高亮文本**：`==文本==`，禁止使用 HTML `<mark>` 标签
- **行内脚注**：`文本^[脚注内容]`
- **引用脚注**：`文本[^1]` + `[^1]: 脚注内容`
- **行内公式**：`$E = mc^2$`
- **块级公式**：`$$...$$` 或 ```math 代码块

---

## 元数据规范（YAML Frontmatter）

### 键值命名

**强制使用 `snake_case`**（全小写下划线），便于 Dataview 查询：

```yaml
# 正确
date_created: 2025-11-28
related_services: [auth, payment]

# 错误 - 禁止使用
dateCreated: 2025-11-28    # camelCase
DateCreated: 2025-11-28    # PascalCase
Date Created: 2025-11-28   # 空格
```

### 基础 Schema（所有文档通用）

```yaml
---
title: 文档标题
date: 2025-11-28                # 创建日期
tags: [topic/subtopic]          # 嵌套标签
aliases: [别名1, 别名2]          # 用于模糊搜索
---
```

### 文档类型 Schema

#### ADR（架构决策记录）

```yaml
---
type: adr
adr_id: 5                       # 决策编号
status: accepted                # proposed → accepted | rejected | deprecated | superseded
date_decided: 2025-11-28
deciders: [UserA, UserB]
superseded_by: adr-012          # 仅当 status = superseded 时填写
technical_debt: low             # none | low | medium | high
tags: [architecture, database]
---
```

**状态机**：`proposed` → `accepted` | `rejected` | `deprecated` | `superseded`

#### Bug 记录

```yaml
---
type: bug
status: open                    # open | resolved | wontfix
severity: high                  # critical | high | medium | low
date: 2025-11-28
resolved_date: 2025-11-29       # 仅当 status = resolved 时填写
tags: [bug, redis]
---
```

#### TIL（每日学习）

```yaml
---
type: til
date: 2025-11-28
topics: [java, spring]
tags: [til]
---
```

#### 开发日志

```yaml
---
type: devlog
date: 2025-11-28
commits: 5
modules: [ingest, registry]
tags: [devlog]
---
```

#### Design（设计文档）

```yaml
---
type: design
status: draft                   # draft | completed
date: 2025-11-28
module: ingest                  # 所属模块
related_adrs: [ADR-005]         # 关联的架构决策
tags: [design/domain]
---
```

**状态机**：`draft` → `completed`

---

## 分类策略

| 机制 | 作用 | 最佳实践 |
|------|------|----------|
| **文件夹** | 物理边界/所有权（排他性） | 用于领域划分（devlog/bugs/learning），一个文件只能属于一个文件夹 |
| **标签** | 横切关注点（非排他性） | 使用嵌套标签 `#topic/subtopic`，如 `#tech/java`、`#status/wip` |
| **链接** | 知识关联（外键） | 仅链接到定义、上下文或强相关实体，避免过度链接 |

**反例**：
- 禁止创建 TODO/Doing/Done 文件夹管理状态，应使用 Frontmatter 的 `status` 字段
- 禁止给每个"Java"单词都加 `[[Java]]` 链接（反向链接污染）

---

## MOC 策略

每个领域文件夹必须有 `_MOC.md` 作为索引页：

| 文件夹 | MOC 文件 | 作用 |
|--------|----------|------|
| `devlog/` | `devlog/_MOC.md` | 开发日志索引 |
| `bugs/` | `bugs/_MOC.md` | Bug 记录索引 |
| `learning/` | `learning/_MOC.md` | 学习材料索引 |
| `decisions/` | `decisions/_MOC.md` | ADR 索引 |
| `designs/` | `designs/_MOC.md` | 设计文档索引 |

**MOC 内容**：
1. 该领域的简要说明
2. Dataview 查询（自动生成列表）
3. 关键笔记的手动入口

---

## Dataview 查询

Dataview 用于动态生成内容列表，**禁止手动维护索引**。

```dataview
LIST FROM "docs/til/2025"
SORT date DESC
```

常用查询模式：

```dataview
TABLE status, date_decided as "决策日期"
FROM "decisions"
WHERE type = "adr" AND status = "proposed"
SORT adr_id DESC
```
