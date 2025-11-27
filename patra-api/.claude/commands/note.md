---
description: 记录 Bug、学习笔记(TIL)或架构决策(ADR)
argument-hint: [bug | til | adr | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**：

| 参数值 | 记录类型 | 存放目录 | 模板 |
|--------|----------|----------|------|
| `bug` | Bug 记录 | `docs/bugs/YYYY/MM/` | `bug-simple.md` 或 `bug-detailed.md` |
| `til` / `study` / `学习` | 学习笔记 | `docs/til/{category}/` | `til.md` |
| `adr` / `决策` | 架构决策 | `docs/decisions/` | `adr.md` |
| 空 | **根据上下文推断** | - | - |

---

## 上下文推断规则

当 `$ARGUMENTS` 为空时，根据当前对话上下文推断记录类型：

| 上下文特征 | 推断类型 |
|------------|----------|
| 刚修复了一个 Bug、解决了一个报错 | `bug` |
| 学到了新知识、发现了技巧、理解了原理 | `til` |
| 做出了技术选型、架构决策、方案对比 | `adr` |
| 无法推断 | **询问用户** |

---

## 执行流程

### 第一步：确定记录类型

```
用户参数: $ARGUMENTS
```

1. 如果参数明确指定了类型 → 使用该类型
2. 如果参数为空 → 分析当前对话上下文，推断类型
3. 如果无法推断 → 使用 AskUserQuestion 询问用户

---

### 第二步：收集记录信息

根据记录类型，从对话上下文中提取关键信息：

#### Bug 记录需要收集：

| 字段 | 来源 | 必需 |
|------|------|------|
| 问题现象 | 用户描述或错误日志 | ✅ |
| 原因 | 调试过程中发现的根因 | ✅ |
| 解决方案 | 修复代码或配置 | ✅ |
| 严重程度 | 根据影响范围推断 | ✅ |
| 所属模块 | 根据文件路径推断 | ✅ |
| 相关提交 | 最近的 git commit | 可选 |
| 根因分析 | 如果问题复杂，进行 Five Whys | 可选 |

**严重程度判断**：
- `critical`: 系统崩溃、数据丢失
- `high`: 核心功能不可用、架构性问题
- `medium`: 功能异常但有绑定方案
- `low`: 小问题、优化项

#### TIL 记录需要收集：

| 字段 | 来源 | 必需 |
|------|------|------|
| 知识点标题 | 总结学到的内容 | ✅ |
| 场景 | 为什么需要学这个 | ✅ |
| 核心知识 | 关键概念和原理 | ✅ |
| 代码示例 | 实际代码片段 | ✅ |
| 分类 | spring/mybatis/java/architecture/ai-coding | ✅ |
| 来源 | debugging/reading/experiment/ai-suggestion | ✅ |
| 置信度 | 是否经过验证 | ✅ |

**分类判断**：
- `spring`: Spring Boot/Cloud/Framework 相关
- `mybatis`: MyBatis/MyBatis-Plus 相关
- `java`: Java 语言特性、JDK API
- `architecture`: 设计模式、架构原则、DDD
- `ai-coding`: AI 辅助编程技巧

#### ADR 记录需要收集：

| 字段 | 来源 | 必需 |
|------|------|------|
| 决策标题 | 简洁描述决策内容 | ✅ |
| 背景 | 为什么需要做这个决策 | ✅ |
| 决策内容 | 具体选择了什么 | ✅ |
| 正面影响 | 带来的好处 | ✅ |
| 负面影响 | 可能的代价 | ✅ |
| 替代方案 | 考虑过的其他方案 | 可选 |

---

### 第三步：确定文件路径

#### Bug 文件路径

```
docs/bugs/{YYYY}/{MM}/BUG-{NNN}-{slug}.md
```

**NNN 生成规则**：查询 `docs/bugs/{YYYY}/` 目录下现有文件，取最大编号 +1

**slug 生成规则**：从标题提取关键词，用 `-` 连接，全小写

**示例**：`docs/bugs/2025/11/BUG-001-transaction-not-rollback.md`

#### TIL 文件路径

```
docs/til/{category}/{YYYY-MM-DD}-{slug}.md
```

**示例**：`docs/til/spring/2025-11-27-conditional-annotation-priority.md`

#### ADR 文件路径

```
docs/decisions/ADR-{NNN}-{slug}.md
```

**NNN 生成规则**：查询 `docs/decisions/` 目录下现有 ADR 文件，取最大编号 +1

**示例**：`docs/decisions/ADR-004-choose-redisson-for-distributed-lock.md`

---

### 第四步：生成文件内容

根据模板和收集的信息，生成完整的 Markdown 文件。

**模板位置**：
- Bug: `docs/templates/bug-simple.md` 或 `docs/templates/bug-detailed.md`
- TIL: `docs/templates/til.md`
- ADR: `docs/templates/adr.md`

**选择 Bug 模板的规则**：
- 如果需要 Five Whys 根因分析（问题复杂、架构性问题）→ `bug-detailed.md`
- 否则 → `bug-simple.md`

---

### 第五步：创建文件并确认

1. 创建必要的目录（如 `docs/bugs/2025/11/`）
2. 写入文件内容
3. 展示创建结果给用户确认

---

## 输出格式

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📝 知识记录已创建
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 文件：docs/bugs/2025/11/BUG-001-xxx.md
📋 类型：Bug 记录
🏷️ 标签：#mybatis #transaction

📄 内容预览：
---
id: BUG-2025-001
severity: high
status: fixed
...
---

# 事务未正确回滚

## 现象
...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💡 提示：在 Obsidian 中打开 docs/ 目录查看
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 使用示例

```bash
# 明确指定类型
/note bug          # 记录 Bug
/note til          # 记录学习笔记
/note study        # 同上
/note adr          # 记录架构决策

# 根据上下文推断
/note              # 自动推断类型
```

---

## 注意事项

1. **从上下文提取信息**：不要询问用户已经在对话中提供过的信息
2. **自动填充字段**：日期、编号、模块等可自动推断的字段不要询问
3. **简洁记录**：重点是「问题是什么」「为什么发生」「如何解决」
4. **双向链接**：如果涉及其他已有记录，使用 `[[]]` 创建链接
5. **立即可用**：创建的文件应该内容完整，用户无需再编辑
