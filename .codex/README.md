# Codex 配置说明

本目录是 Patra 项目的 Codex 执行入口，目标是让研发规范、质量门和角色化能力在同一套流程下稳定运行。

## 目录结构

- `.codex/prompts/`：可直接调用的工作流提示词
- `.codex/skills/`：可复用技能（代码开发、审查、测试、排障）

## 规则入口

- 项目规范主入口：`AGENTS.md`
- 流程化任务：`.codex/prompts/`
- 角色化能力：`.codex/skills/`

## 推荐使用顺序

1. 编写/修改 Java 代码前，使用 `$java-development`
2. 变更完成后执行 `/prompts:post-code`
3. 需要提交时执行 `/prompts:commit`
## 维护策略

- 修改规则时优先更新 `AGENTS.md`
- `.codex/prompts/` 和 `.codex/skills/` 只放可执行流程，不重复堆叠架构规则全文
- 新增流程优先落在 `.codex/prompts/`，新增通用能力优先落在 `.codex/skills/`
