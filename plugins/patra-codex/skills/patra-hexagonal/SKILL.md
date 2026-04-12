---
name: patra-hexagonal
description: |
  Patra 六边形架构组件创建与编排指南。创建或实现 Controller、Handler、Port、Adapter、Repository、Gateway、QueryService、REST/RPC 接口、XXL-Job、Starter 选择时使用。
  触发场景：用户要求“添加”“创建”“实现”功能或组件，需要决定代码放在哪一层、如何划分写模型与读模型、如何定义端口与适配器，或需要选择 Starter。
  不适用于 JPA 数据层细节、事件通信或异常排查；这些场景分别切到 `patra-jpa`、`patra-events`、`patra-troubleshooter`。
---

# Patra 六边形架构

在 Patra 中创建新功能或组件时，用本技能快速确定分层、端口、入口和 Starter。

## Quick Start

1. 先读以下规则文件：
- `.claude/rules/project-info.md`
- `.claude/rules/layers/adapter.md`
- `.claude/rules/layers/application.md`
- `.claude/rules/layers/domain.md`
- `.claude/rules/layers/infrastructure.md`
- `.claude/rules/tech/commandbus.md`
- `.claude/rules/tech/port-service.md`
2. 先判断需求属于写模型还是读模型，再判断入口类型：HTTP、任务、消息或内部调用。
3. 先套一个最小骨架，再去实现细节：
   - HTTP 写：`Controller -> ApiConverter -> Command -> Handler -> Aggregate/Port -> Adapter`
   - HTTP 读：`Controller -> ApiConverter -> QueryService -> ReadPort -> ReadAdapter`
   - 任务或消息入口：`Adapter 入口 -> App 编排 -> Domain 端口 -> Infra 实现`
4. 只有在遇到模板、Starter 或 Port 细节时，再读 `references/` 里的对应文件。

## Workflow

1. 先定义 app 层用例边界：`Command` / `Result` 或 `QueryService` / `Query DTO`。
2. 再定义 domain 层端口与聚合职责，不要先从 infra 反推模型。
3. 再实现 infra 适配器和 Starter 依赖。
4. 最后补齐 adapter 层协议转换、参数校验和测试。
5. 遇到 JPA、事件链或排障问题时，立即切换对应 skill，不要在本 skill 内硬撑。

## Quality Rules

- Adapter 写操作只注入 `CommandBus`，不要直接注入 Handler 或 Repository。
- 查询只走 `QueryService`，不要从 Controller 直接访问 Repository。
- `Command` 负责应用层输入约束，`Result` 负责业务语义，不要直接暴露原始 `Long`、`boolean`、`void`。
- 事务只放在 Application 层，不要下沉到 Adapter 或 Infra。
- Port 命名遵循最小语义：
  - 聚合持久化：`{Entity}Repository`
  - 外部能力：`{Function}Port`
  - 读端查询：`{Entity}ReadPort`
  - 应用层门面：`{Entity}Gateway`

## Reference Map

- `references/controller-guide.md`：写 Controller、查询 Controller、XXL-Job 时读取。
- `references/handler-guide.md`：写 `Command`、`Handler`、事务拆分和 QueryService 时读取。
- `references/patra-starters.md`：不确定 Starter 选择时读取。
- `references/configuration.md`：需要补配置项或配置类时读取。
- `references/port-service-guide.md`：需要细分 Port 类型和目录落位时读取。

## Switch Skills

- 写 Entity / Dao / JpaMapper / RepositoryAdapter：切到 `patra-jpa`
- 写事件监听、Outbox、可靠消息：切到 `patra-events`
- 排查异常、日志、调用链：切到 `patra-troubleshooter`

## Validation

- 检查写接口是否只通过 `CommandBus` 进入应用层。
- 检查查询链路是否落在 `QueryService -> ReadPort -> ReadAdapter`。
- 检查是否把事务、协议转换或基础设施细节放错层。
- 检查本次实现是否已经切到更专门的 skill。
