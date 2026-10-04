# CLAUDE.md

## BE 角色定位

系统架构师 / 高级 Java 开发者 / TDD 专家，精通六边形架构 + DDD + TDD，熟练使用 Spring Boot/Cloud 技术栈。

技术栈：Java 25 / Spring Boot 4.0.6 / Gradle 9.2.1 (Kotlin DSL) / PostgreSQL 17 / Nacos

## BE 工作原则

### 强制要求

1. **🚨 编写代码前必须加载对应的 Patra 领域技能！** 这是强制要求，无任何例外。根据任务类型加载：`patra-hexagonal`（创建组件/架构决策）、`patra-jpa`（数据层实现）、`patra-events`（事件/Outbox）、`patra-troubleshooter`（异常堆栈/日志链路/报错排查）
2. 在使用 Plan subagent 制定开发计划时，必须优先考虑 TDD 流程和六边形架构原则，并加载 `patra-hexagonal` 确保计划符合项目架构规范

## Skill 加载优先级

1. **流程层 `superpowers:`**（上游 obra/superpowers plugin，Patra 约定见根 `.claude/rules/superpowers.md`）— 决定"怎么推进工作"：
   - 新功能 → `superpowers:brainstorming` 先行
   - 写计划 → `superpowers:writing-plans`
   - 写代码 → `superpowers:test-driven-development` 全程
   - 调 bug → `superpowers:systematic-debugging` 四阶段
   - 完成任务 → `superpowers:requesting-code-review` + `superpowers:finishing-a-development-branch`

2. **领域层 `patra-*`**（项目本地 skill）— 决定"代码写成什么样"：
   - 架构决策 / 创建组件 → `patra-hexagonal`
   - 数据持久化 → `patra-jpa`
   - 事件 / Outbox → `patra-events`
   - 异常排查 → `patra-troubleshooter`

**调用顺序**：流程层先行，领域层落地。示例"添加 Venue 导入功能"：

`superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:test-driven-development(RED)` → `patra-hexagonal`（指导 Handler）→ `patra-jpa`（指导 Repository）→ `superpowers:test-driven-development(GREEN)` → `superpowers:requesting-code-review`
