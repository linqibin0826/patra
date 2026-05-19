---
name: requesting-code-review
description: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
---

# 请求代码审查

派遣代码审查子代理，在问题扩散之前发现它们。审查者获得的是精心组织的评估上下文——绝不是你的会话历史。这样可以让审查者专注于工作成果而非你的思考过程，同时保留你自己的上下文以便继续工作。

**核心原则：** 早审查，勤审查。

## 何时请求审查

**必须审查：**
- 子代理驱动开发中每个任务完成后（详见 `patra:subagent-driven-development`）
- 完成重要功能后
- 合并到 main 之前

**可选但有价值：**
- 卡住时（换个视角）
- 重构之前（建立基线）
- 修复复杂 bug 之后

## 如何请求

**1. 获取 git SHA：**
```bash
BASE_SHA=$(git rev-parse HEAD~N)   # N = 本次任务起始之前的 commit 数
HEAD_SHA=$(git rev-parse HEAD)
```

也可以从 plan.html 里 `<article id="task-N">` 的 `data-baseline-sha` 属性直接读 baseline SHA（writing-plans 输出的 plan 会记录）。

**2. 派遣代码审查子代理：**

使用 Task 工具，`subagent_type: general-purpose`，填写 `code-reviewer.md` 中的模板。

> **注：** 项目曾有独立的 `code-reviewer` agent（`.claude/agents/code-reviewer.md`），已迁移到本 skill 的提示词模板，统一通过 `Skill` 工具维护。

**占位符说明：**
- `{DESCRIPTION}` —— 你刚完成的内容简要说明
- `{PLAN_OR_REQUIREMENTS}` —— plan.html 路径 + task ID，或需求文本
- `{BASE_SHA}` —— 起始 commit
- `{HEAD_SHA}` —— 结束 commit

**3. 处理反馈：**
- Critical 问题立即修复
- Important 问题在继续之前修复
- Minor 问题记录下来稍后处理
- 如果审查者有误，用技术理由反驳

## 示例

```
[刚完成任务 2：添加 IngestRequest 校验]

你：让我在继续之前请求代码审查。

BASE_SHA=$(git rev-parse HEAD~3)         # 任务 2 包含 3 个 commit
HEAD_SHA=$(git rev-parse HEAD)

[派遣代码审查子代理]
  subagent_type: general-purpose
  DESCRIPTION: 在 IngestRequest 上加 ProvenanceCode VO 校验，含 @NotBlank + record 紧凑构造器双层防御
  PLAN_OR_REQUIREMENTS: docs/patra/plans/ingest-validation-plan.html 任务 2
  BASE_SHA: a7981ec
  HEAD_SHA: 3df7661

[子代理返回]:
  优点：六边形架构守得很干净，TestContainers 集成测试完整
  问题：
    Critical：Controller 绕过 CommandBus 直接注入 Handler
    Important：record 缺少 List.copyOf 防御性拷贝
    Minor：JavaDoc 用了 /** */ 而非 ///
  评估：修完再合

你：[修复 CommandBus 和防御性拷贝]
[继续任务 3]
```

## 与工作流的集成

**子代理驱动开发：**
- 每个任务完成后审查
- 在问题叠加之前发现它们
- 修复后再进入下一个任务

**执行计划：**
- 每个任务完成后或在自然 checkpoint 审查
- 获取反馈，应用，继续

**临时开发：**
- 合并前审查
- 卡住时审查

## 红线

**绝不要：**
- 因为"很简单"就跳过审查
- 忽略 Critical 问题
- 带着未修复的 Important 问题继续推进
- 对合理的技术反馈进行争辩

**如果审查者有误：**
- 用技术理由反驳
- 展示证明其可行的代码/测试
- 要求澄清

参见模板：`requesting-code-review/code-reviewer.md`
