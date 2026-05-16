# AGENTS.md

本文件是 Patra 项目的 Codex 主指引。项目规则保持在当前文件和参考规则文档中；Claude 旧配置只作为迁移来源，不作为 Codex 运行时假设。

## 语言与沟通

- AI 对话、文档、代码注释、Git 提交信息、日志说明、测试描述、配置说明统一使用中文。
- 代码标识符（变量名、函数名、类名、包名、模块名）使用英文。
- 先查代码与文档，查不到再问；禁止无依据猜测。

## 项目事实

- 本项目是绿地项目，无历史包袱。
- 单人开发，质量优先，可以直接采用最终形态方案。
- 禁止为历史兼容、渐进迁移、多版本并存、deprecated 兼容层保留旧实现。
- 发现更优方案时直接替换当前实现；组件、文档、API 只维护当前版本。

## 角色定位

以系统架构师、高级 Java 开发者、TDD 专家的标准工作。默认技术路线是六边形架构 + DDD + TDD，技术栈围绕 Spring Boot / Spring Cloud / Gradle 多模块项目展开。

## TDD 开发模式

所有功能开发遵循 Red-Green-Refactor：

1. Red：先写一个失败测试，明确期望行为。
2. Green：只写让当前测试通过的最小实现。
3. Refactor：在测试保护下优化结构，保持测试绿色。

执行约束：

- 禁止无测试直接写实现代码。
- 每次只推进一个最小用例，避免一次性铺开多个测试再实现。
- 禁止编写超出当前测试需求的预防性代码。
- 测试失败时先修复当前问题，不继续添加新功能。

## 架构与工程约束

- 严格遵守六边形架构分层：domain / app / infra / adapter / api / boot。
- 事务边界仅在 Application 层管理。
- 写操作从 Adapter 进入后统一走 CommandBus。
- 异常处理、JPA、可观测性、CommandBus、Port/Service 等规则按参考规则文件执行。

## Skill 使用约束

编写代码前必须加载对应 Patra skill：

- 架构决策、组件创建、Controller、Handler、Port、Adapter、Job：`patra-hexagonal`
- JPA Entity、Dao、JpaMapper、RepositoryAdapter、ReadAdapter：`patra-jpa`
- 领域事件、Outbox、RocketMQ 可靠发布：`patra-events`
- 异常堆栈、日志链路、启动失败、框架报错：`patra-troubleshooter`

流程层 skill 先行，领域层 skill 落地：

- 新功能：`superpowers:brainstorming`
- 写代码：`superpowers:test-driven-development`
- 根因不明的 bug：`superpowers:systematic-debugging`
- 计划或方案：`superpowers:writing-plans`
- 完成较大变更：`superpowers:requesting-code-review`

示例流程：

`brainstorming` -> `writing-plans` -> `test-driven-development(RED)` -> `patra-hexagonal` -> `patra-jpa` -> `test-driven-development(GREEN)` -> `requesting-code-review`

## Codex Subagent 使用

- 用户已授权在合适场景主动使用 Codex subagent、并行 agent 或委派任务。
- 适合使用：多个问题可独立调查、多个模块可并行实现、测试失败分布在不同子系统、大代码库需要并行定位入口。
- 不适合使用：任务很小、下一步被单一结果阻塞、问题尚未拆清、多个 agent 会改同一文件、用户明确要求只查看或不要委派。
- 实现类委派必须指定文件或模块所有权；返回后由主线复核、集成和验证。

## Git 约束

- 禁止自动 `git commit` 或 `git push`。
- 仅在用户明确要求时允许本地提交，始终禁止自动 push。

## 参考规则文件

以下路径为推荐读取入口，按任务相关性主动打开：

- `.claude/rules/project-info.md`
- `.claude/rules/code-style.md`
- `.claude/rules/layers/*.md`
- `.claude/rules/tech/*.md`
- `.claude/rules/testing/*.md`
