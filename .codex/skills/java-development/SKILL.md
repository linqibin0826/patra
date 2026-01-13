---
name: java-development
description: Patra Java/Spring Boot 开发指南（六边形架构 + DDD + TDD），用于本仓库 Java 代码编写/修改/重构（Controller/Handler/Repository、CommandBus、JPA、HTTP Interface、Consul、Starter 依赖、测试）。
metadata:
  short-description: Patra Java 开发规范与模板
---

# Patra Java 开发指南

本技能用于在 Patra 代码库内进行 Java 开发与重构，目标是：**遵循六边形架构分层、统一入口（CommandBus）、严格控制事务边界、并以测试驱动交付**。

## 0. 先读项目规则

- 开始前先遵守仓库根 `AGENTS.md` 的分层与技术规范（尤其是：`domain/app/infra/adapter/api/boot` 的职责与禁止项）。

## 1. 开发默认工作流（强制推荐）

1. 明确需求与边界：写操作还是读操作？是否涉及跨服务调用/持久化/消息？
2. 选择模块与层级：优先定位到具体微服务的 `*-adapter/*-app/*-domain/*-infra`。
3. TDD：先写失败测试，再最小实现，再重构（Red-Green-Refactor）。
4. 写路径统一使用 `CommandBus`：Adapter 只注入 `CommandBus`，Application 用 `*Handler` 编排并声明事务。
5. 读路径不走 `CommandBus`：直接注入 `*QueryService`（或只读仓储）即可。

## 2. 常见场景速查（按需加载参考资料）

### 2.1 新增/修改 REST 接口（Adapter）

- 目标：Controller 只做协议转换与参数装配，不写业务逻辑。
- 参考：`references/controller-patterns.md`

### 2.2 新增写用例（Application + CommandBus）

- 目标：`{Action}{Entity}Command` + `{Action}{Entity}Handler` + `{Action}{Entity}Result`；事务只在 `Handler.handle()`。
- 关键：参数校验放在 `Command` 的 compact constructor；不要在 Adapter 里做业务校验/转换。
- 参考：
  - `AGENTS.md`（CommandBus/事务/异常）
  - `references/transaction-error-handling.md`

### 2.3 新增持久化（Infrastructure + JPA）

- 目标：Entity 继承 `BaseJpaEntity`；需要软删除时继承 `SoftDeletableJpaEntity`；MapStruct 做映射；批量保存控制 `flush()/clear()`。
- 参考：`references/jpa-patterns.md`

### 2.4 适配层/基础设施层模式（Ports/Adapters）

- 目标：通过 `Port/Repository` 抽象外部依赖；Infrastructure 仅实现端口，不反向依赖 Application/Adapter。
- 参考：`references/adapter-layer-patterns.md`

### 2.5 事件驱动与 Outbox

- 目标：跨用例协作优先事件驱动；避免 Handler 调 Handler。
- 参考：
  - `references/event-driven-architecture.md`
  - `references/outbox-pattern.md`

### 2.6 配置管理（Consul / ConfigurationProperties）

- 目标：配置不硬编码；用 `@ConfigurationProperties` 绑定；敏感配置注意隔离与加密策略。
- 参考：`references/configuration-management.md`

### 2.7 Patra Starter 选型

- 目标：优先使用 Patra 自定义 Starter；避免直接引入原始依赖与重复实现。
- 参考：`references/patra-starters-guide.md`

## 3. 完成前自检清单

- 分层依赖方向正确（Domain 无框架依赖；事务仅在 Application；写入口只注入 CommandBus）。
- 异常体系符合规范（DomainException / ApplicationException / RemoteCallException）。
- JPA 规范满足（`BaseJpaEntity/SoftDeletableJpaEntity`、软删除适用范围、批量与 ID 策略）。
- 测试按分层命名与类型补齐（`*Test/*IT/*E2E`），避免使用废弃 `@MockBean`。
