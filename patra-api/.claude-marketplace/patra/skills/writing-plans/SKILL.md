---
name: writing-plans
description: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
---

# 编写计划

## 概述

编写全面的实现计划，假设工程师对我们的代码库零上下文，且品味存疑。记录他们需要知道的一切：每个任务要修改哪些文件、代码、测试、可能需要查阅的文档、如何测试。将整个计划拆成小步骤任务。DRY。YAGNI。TDD。频繁 commit。

假设他们是有经验的开发者，但对我们的工具链和问题领域几乎一无所知。假设他们不太擅长测试设计。

**开始时宣布：** "我正在使用 writing-plans 技能创建实现计划。"

**上下文：** 此技能应在专用 worktree 中运行（由 brainstorming 技能创建）。

**计划保存位置：** `docs/patra/plans/YYYY-MM-DD-<feature-name>.html`
- 用户对计划位置的偏好优先于此默认值
- **产物是 HTML，不是 Markdown**——通过 `data-status` 属性追踪进度比 markdown checkbox 表达力强
- 复制 `template/plan-template.html`，按 `<!-- FILL: -->` 注释填内容；不要改 CSS / 结构

## 范围检查

如果规格涵盖了多个独立子系统，它应该在头脑风暴阶段就被拆分为子项目规格。如果没有，建议将其拆分为独立的计划——每个子系统一个。每个计划应该能独立产出可工作、可测试的软件。

## 文件结构

在定义任务之前，先列出将要创建或修改的文件以及每个文件的职责。这是锁定分解决策的地方。

- 设计边界清晰、接口定义良好的单元。每个文件应有一个明确的职责。
- 你对能一次放入上下文的代码推理得最好，文件越专注你的编辑越可靠。优先选择小而专注的文件，而非承担过多功能的大文件。
- 一起变更的文件应放在一起。按职责拆分，而非按技术层级拆分。
- 在现有代码库中，遵循已有模式。如果代码库使用大文件，不要单方面重构——但如果你正在修改的文件已经变得难以管理，在计划中包含拆分是合理的。

此结构决定了任务分解。每个任务应产出独立的、有意义的变更。

## 小步骤任务粒度

**每步是一个操作（2-5 分钟）：**
- "编写失败的测试" - 一步
- "运行它确认失败" - 一步
- "实现最少代码让测试通过" - 一步
- "运行测试确认通过" - 一步
- "Commit" - 一步

## 计划文档结构（HTML 模板）

复制 `template/plan-template.html` 到 `docs/patra/plans/YYYY-MM-DD-<feature-name>.html`，按文件内所有 `<!-- FILL: -->` 注释填内容。**禁止修改 CSS、章节顺序、HTML 结构** —— 只填内容。

模板已包含：
- 三栏布局（左 TOC 含任务子列表 / 中 任务卡片 / 右 Implementation Notes）
- task / step 卡片结构（含 `data-status` 状态机 5 类渲染：pending / in-progress / done / blocked / skipped）
- 左栏 `<ol class="toc-tasks">` 任务子列表（`data-task-status` 与中栏 article `data-status` 一一对应，渲染同色状态符号 ○ / ▸ / ● / × / ⊘）
- 右栏 4 类 note entry 模板（decision / change / tradeoff / other）+ stats footer

**新增 task 时双写**（必须同步，否则 TOC 与中栏对不上）：
1. 复制 task 卡片模板到 `<section id="tasks">` 内（HTML 注释保留供复制），`<article id="task-N" data-status="pending">`
2. 复制 TOC 模板到左栏 `<ol class="toc-tasks">` 内：`<li data-task-status="pending"><a href="#task-N">Task N — [任务名]</a></li>`（注释保留原位）

## 绿地项目 YAGNI 强化

patra-api 是单人开发的绿地项目（无历史包袱），编计划时主动剔除以下维度——它们都是 YAGNI 反模式：

- ❌ "向后兼容步骤" / "数据迁移" / "灰度发布" / "分阶段实施"
- ❌ "团队协作妥协"维度（如"先用兼容方案，等团队达成共识后再重构"）
- ❌ 任何"如果时间允许 / 建议后续优化"的语义包装

直接采用最优方案，单一版本切换。

## 禁止占位符

每个步骤都必须包含工程师需要的实际内容。以下是**计划缺陷**——绝不要写出来：
- "待定"、"TODO"、"后续实现"、"补充细节"
- "添加适当的错误处理" / "添加验证" / "处理边界情况"
- "为上述代码编写测试"（没有实际测试代码）
- "类似任务 N"（重复代码——工程师可能不按顺序阅读任务）
- 只描述做什么而不展示怎么做的步骤（代码步骤必须有代码块）
- 引用了未在任何任务中定义的类型、函数或方法

## 注意事项
- 始终使用精确的文件路径
- 每个步骤都包含完整代码——如果步骤涉及代码变更，就展示代码
- 精确的命令和预期输出
- DRY、YAGNI、TDD、频繁 commit
- 每个 `<article class="task">` 必须有 `id="task-N"` 与 `data-status="pending"`
- 每个 `<li class="step">` 必须有 `data-status="pending"`

## 自检

编写完整计划后，以全新视角审视规格并对照检查计划。这是你自己执行的检查清单——不是子代理调度。

**1. 规格覆盖度：** 浏览规格中的每个章节/需求。你能指出实现它的任务吗？列出所有遗漏。

**2. 占位符扫描：** 搜索计划中的红旗——上方"禁止占位符"章节中的任何模式。修复它们。**额外**：grep 文件中所有 `<!-- FILL: -->` 注释是否已替换完毕。

**3. 类型一致性：** 后续任务中使用的类型、方法签名和属性名是否与前面任务中定义的一致？任务 3 中叫 `save()` 但任务 7 中叫 `saveAll()` 就是 bug。

**4. HTML 结构完整性：**
- 每个 `<article class="task">` 都有唯一 `id="task-N"` 与初始 `data-status="pending"`？
- 每个 `<li class="step">` 都有初始 `data-status="pending"`？
- 头部 `<dl class="meta">` 含目标 / 架构 / 技术栈 / 关联 spec 四项？
- `<style>` 块 inline 在 `<head>`，无外部 CSS / JS 依赖（字体除外）？
- `[data-status]` 属性选择器在 CSS 里都定义了对应渲染规则？
- TOC 任务子列表与中栏 article 一一对应？`<ol class="toc-tasks">` 内 li 数量 = `<article class="task">` 数量；每个 article 的 `id="task-N"` 都能在 toc-tasks 的某条 `<a href="#task-N">` 找到；初始 `data-task-status="pending"` 与 article `data-status="pending"` 一致

如果发现问题，直接内联修复。无需重新审查——修好继续推进。如果发现规格中的需求没有对应任务，就添加任务。

## 执行交接

保存计划后，提供执行选项：

**"计划已完成并保存到 `docs/patra/plans/<filename>.html`。两种执行方式：**

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

**选哪种方式？"**

**如果选择子代理驱动：**
- **必需子技能：** 使用 patra:subagent-driven-development
- 每个任务一个新子代理 + 两阶段审查

**如果选择内联执行：**
- **必需子技能：** 使用 patra:executing-plans
- 批量执行并设有检查点供审查
