---
description: 记录 Bug、学习笔记(TIL)、架构决策(ADR)、学习材料(Learning)或设计文档(Design)
argument-hint: [bug | til | adr | learning | design | 空]
---

## 参数说明

**用户传入的参数**: `$ARGUMENTS`

**参数解析规则**：

| 参数值 | 记录类型 | 存放目录 | 说明 |
|--------|----------|----------|------|
| `bug` | Bug 记录 | `../Patra-docs/content/bugs/YYYY/MM/` | 问题记录与解决方案 |
| `til` / `study` / `学习` | 每日学习总结 | `../Patra-docs/content/til/{YYYY}/{MM}/` | **一天结束后**的学习汇总 |
| `adr` / `决策` | 架构决策 | `../Patra-docs/content/decisions/` | 技术选型和架构决策 |
| `learning` / `教程` | 详细学习材料 | `../Patra-docs/content/learning/{topic}/` | 完整的教程内容 |
| `design` / `设计` | 设计文档 | `../Patra-docs/content/designs/{module}/` | 功能/模块的架构设计 |
| 空 | **根据上下文推断** | - | - |

---

## 上下文推断规则

当 `$ARGUMENTS` 为空时，根据当前对话上下文推断记录类型：

| 上下文特征 | 推断类型 |
|------------|----------|
| 刚修复了一个 Bug、解决了一个报错 | `bug` |
| 刚完成了一个知识点/章节的详细讲解 | `learning` |
| 用户说"今天学完了"、"总结一下今天学的" | `til` |
| 做出了技术选型、架构决策、方案对比 | `adr` |
| 讨论了功能架构设计、组件关系、数据流 | `design` |
| 无法推断 | **询问用户** |

---

## 各类型详细说明

### Bug 记录

**触发时机**：修复了一个非平凡的 Bug

**需要收集的信息**：

| 字段 | 来源 | 必需 |
|------|------|------|
| 问题现象 | 用户描述或错误日志 | ✅ |
| 原因 | 调试过程中发现的根因 | ✅ |
| 解决方案 | 修复代码或配置 | ✅ |
| 严重程度 | 根据影响范围推断 | ✅ |
| 所属模块 | 根据文件路径推断 | ✅ |

**文件路径**：`../Patra-docs/content/bugs/{YYYY}/{MM}/BUG-{NNN}-{slug}.md`

**模板**：`../Patra-docs/content/templates/bug-simple.md` 或 `../Patra-docs/content/templates/bug-detailed.md`

---

### Learning 详细学习材料（新增）

**触发时机**：完成了一个知识点/章节的详细讲解，需要保存完整内容

**特点**：
- 包含详细的概念讲解、示例、图表
- 按主题/系列组织（如 `observability/01-core-concepts.md`）
- 是可以日后反复查阅的**完整教程**

**需要收集的信息**：

| 字段 | 来源 | 必需 |
|------|------|------|
| 主题/系列 | 当前学习的主题（如 observability） | ✅ |
| 章节编号 | 序号（如 01, 02） | ✅ |
| 标题 | 章节标题 | ✅ |
| 完整内容 | 对话中讲解的详细内容 | ✅ |

**文件路径**：`../Patra-docs/content/learning/{topic}/{NN}-{slug}.md`

**目录结构示例**：
```
../Patra-docs/content/learning/
├── observability/
│   ├── _index.md           # 系列索引
│   ├── 01-core-concepts.md
│   ├── 02-metrics.md
│   └── 03-logs.md
└── spring-batch/
    ├── _index.md
    └── 01-fundamentals.md
```

**执行流程**：
1. 确定主题和章节编号
2. 从对话上下文提取完整的讲解内容
3. 创建/更新系列索引文件（`_index.md`）
4. 创建章节文件

---

### TIL 每日学习总结（修改后）

**触发时机**：一天的学习结束后，用户主动调用

**重要变更**：
- ❌ 不再是学完一个小节就创建
- ✅ 是一天结束后的学习汇总
- ✅ 可以汇总多个 learning 文件

**信息来源**（按优先级）：
1. **用户说明**：用户在调用时描述今天学了什么
2. **Git 历史**：查询今天新增的 `../Patra-docs/content/learning/**/*.md` 文件
3. **对话上下文**：当前对话中讨论的学习内容

**需要收集的信息**：

| 字段 | 来源 | 必需 |
|------|------|------|
| 日期 | 今天的日期 | ✅（自动） |
| 学习主题 | 今天学习的主题列表 | ✅ |
| 核心收获 | 每个主题的关键要点（简洁） | ✅ |
| 关联的 Learning 文件 | 今天创建的详细学习材料 | ✅ |
| 标签 | 涉及的技术标签 | ✅ |

**文件路径**：`../Patra-docs/content/til/{YYYY}/{MM}/{YYYY-MM-DD}-{slug}.md`

**目录结构示例**：
```
../Patra-docs/content/til/
├── _MOC.md           # 总索引
├── 2025/
│   ├── 11/
│   │   ├── 2025-11-28-observability-basics.md
│   │   └── 2025-11-29-metrics-deep-dive.md
│   └── 12/
│       └── 2025-12-01-logs-and-tracing.md
└── 2026/
    └── ...
```

**TIL 内容结构**：
```markdown
---
date: 2025-11-28
tags: [observability, metrics, logs]
learning_series: observability
chapters_completed: [01, 02]
---

# 今日学习：可观测性基础

## 学习概要

今天系统学习了可观测性（Observability）的核心概念，包括：
- 可观测性与监控的区别
- 三大支柱：Metrics / Logs / Traces
- 信号之间的关联

## 核心收获

### 1. 可观测性 vs 监控
- 监控：已知问题的阈值告警
- 可观测性：回答未知问题的能力

### 2. 三大支柱
- Metrics：聚合数值，适合告警
- Logs：离散事件，适合调试
- Traces：请求路径，适合追踪

## 详细学习材料

- [[learning/observability/01-core-concepts|第一章：核心概念]]
- [[learning/observability/02-metrics|第二章：Metrics]]

## 明日计划

- 继续学习第三章：Logs
```

**执行流程**：
1. 检查用户是否提供了学习说明
2. 如果没有，查询 git 历史获取今天新增的 learning 文件：
   ```bash
   git diff --name-only --diff-filter=A HEAD~10 -- '../Patra-docs/content/learning/**/*.md'
   # 或按日期
   git log --since="6am" --name-only --diff-filter=A -- '../Patra-docs/content/learning/**/*.md'
   ```
3. 汇总今天的学习内容
4. 生成 TIL 文件，包含：
   - 简洁的学习概要
   - 核心收获要点
   - 指向详细 Learning 文件的链接

---

### ADR 架构决策

**触发时机**：做出了重要的技术选型或架构决策

**需要收集的信息**：

| 字段 | 来源 | 必需 |
|------|------|------|
| 决策标题 | 简洁描述决策内容 | ✅ |
| 背景 | 为什么需要做这个决策 | ✅ |
| 决策内容 | 具体选择了什么 | ✅ |
| 正面影响 | 带来的好处 | ✅ |
| 负面影响 | 可能的代价 | ✅ |
| 替代方案 | 考虑过的其他方案 | 可选 |

**文件路径**：`../Patra-docs/content/decisions/ADR-{NNN}-{slug}.md`

**模板**：`../Patra-docs/content/templates/adr.md`

---

### Design 设计文档

**触发时机**：讨论完功能/模块的架构设计后

**特点**：
- 记录"架构是什么"，不关心实现过程
- 以架构图为核心（组件关系图、数据流图、时序图）
- 可以关联到相关的 ADR 决策

**需要收集的信息**：

| 字段 | 来源 | 必需 |
|------|------|------|
| 设计标题 | 功能/模块名称 | ✅ |
| 所属模块 | 根据讨论内容推断 | ✅ |
| 问题陈述 | 要解决什么问题 | ✅ |
| 目标 | 设计要达成的目标 | ✅ |
| 架构图 | 对话中讨论的架构设计 | ✅ |
| 关联 ADR | 相关的架构决策 | 可选 |

**文件路径**：`../Patra-docs/content/designs/{module}/{slug}.md`

**目录结构示例**：
```
../Patra-docs/content/designs/
├── _MOC.md              # 设计文档索引
├── ingest/              # 数据摄取模块
│   ├── mesh-xml-parser.md
│   └── pubmed-fetcher.md
├── registry/            # 注册模块
│   └── term-matching.md
└── shared/              # 共享/跨模块设计
    └── observability-integration.md
```

**模板**：`../Patra-docs/content/templates/design.md`

**执行流程**：
1. 从对话上下文提取设计方案
2. 确定所属模块和文件名
3. 提取架构图（Mermaid/D2）
4. 填充模板并创建文件
5. 如果模块目录不存在，自动创建

---

## 使用示例

```bash
# 记录 Bug
/note bug

# 保存刚学完的章节（详细内容）
/note learning

# 一天结束后，总结今天的学习
/note til
/note til 今天学了可观测性的核心概念和 Metrics

# 记录架构决策
/note adr

# 记录设计文档（讨论完设计方案后）
/note design

# 根据上下文自动推断
/note
```

---

## 输出格式

### Learning 输出

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📚 学习材料已保存
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 文件：../Patra-docs/content/learning/observability/02-metrics.md
📋 系列：可观测性学习系列
🏷️ 章节：第二章 - Metrics（指标）

📄 内容概要：
├── 四种指标类型
├── Micrometer API
├── Prometheus 与 PromQL
└── 自定义业务指标

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### TIL 输出

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📝 每日学习总结已创建
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 文件：../Patra-docs/content/til/2025/11/2025-11-28-observability-fundamentals.md
📅 日期：2025-11-28
🏷️ 标签：#observability #metrics #logs

📄 今日学习：
├── ✅ 可观测性核心概念
├── ✅ Metrics 指标类型
└── 📎 关联 2 个详细学习材料

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Design 输出

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📐 设计文档已创建
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 文件：../Patra-docs/content/designs/ingest/mesh-xml-parser.md
📦 模块：ingest
🔗 关联 ADR：ADR-003

📄 设计内容：
├── 📋 概述：MeSH XML 解析器架构设计
├── 🏗️ 组件关系图
├── 🔄 数据流图
└── 📊 六边形架构视图

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 注意事项

1. **Learning vs TIL 的区别**：
   - Learning：完整的教程内容，学完一个章节就保存
   - TIL：每日学习汇总，一天结束后创建，指向 Learning 文件

2. **ADR vs Design 的区别**：
   - ADR：记录"为什么选择这个方案"（决策过程、替代方案对比）
   - Design：记录"架构是什么"（组件关系、数据流、架构图）

3. **从上下文提取信息**：不要询问用户已经在对话中提供过的信息

4. **自动填充字段**：日期、编号、模块等可自动推断的字段不要询问

5. **双向链接**：使用 `[[]]` Wikilink 语法创建关联

6. **Git 历史查询**：TIL 可以通过 git 历史自动发现今天的学习内容

7. **Design 模块目录**：如果模块目录不存在，自动创建（如 `../Patra-docs/content/designs/ingest/`）
