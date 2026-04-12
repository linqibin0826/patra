# AGENTS.md

本文件是 Patra 项目的 Codex 主指引，保持简洁；详细规范通过“参考文件”方式读取，不依赖 guaranteed import 机制。

## 语言与沟通

- AI 对话、文档、代码注释、日志说明、测试描述统一使用中文
- 代码标识符（变量名、函数名、类名、包名）使用英文

## 项目事实

- 绿地项目，无历史包袱，不做向后兼容、数据迁移、渐进式重构
- 单人开发，质量优先，允许直接采用最终形态方案
- 发现更优方案可直接替换旧实现，不保留兼容层

## 工作方式

- 先查代码与文档，查不到再问，禁止无依据猜测
- 功能开发默认遵循 TDD：Red -> Green -> Refactor
- 测试先行：禁止无测试直接写实现
- 每次只推进一个最小用例，避免一次性铺开

## 架构与工程约束

- 采用六边形架构 + DDD 分层
- 事务边界仅在 Application 层管理
- 写操作从 Adapter 进入后统一走 CommandBus
- 异常、JPA、可观测性等技术细节遵循对应规则文档

## Skill 使用约束

- 编写代码前必须按任务加载对应 Patra skill
- 架构/组件：`patra-hexagonal`
- 持久化/JPA：`patra-jpa`
- 事件/Outbox：`patra-events`
- 异常与链路排查：`patra-troubleshooter`

## Git 约束

- 禁止自动 `git commit` 或 `git push`
- 仅在用户明确要求时允许本地提交，始终禁止自动 push

## 参考规则文件

以下路径为推荐读取入口（按任务相关性主动打开）：

- `.claude/rules/project-info.md`
- `.claude/rules/code-style.md`
- `.claude/rules/layers/*.md`
- `.claude/rules/tech/*.md`
- `.claude/rules/testing/*.md`
