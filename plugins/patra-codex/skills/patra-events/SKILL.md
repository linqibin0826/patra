---
name: patra-events
description: |
  Patra 事件驱动与 Outbox 模式指南。设计内部领域事件、事务监听器、Outbox 可靠消息发布和事件链时使用。
  触发场景：创建 DomainEvent、实现 `@TransactionalEventListener`、设计事务后处理、实现 Outbox 中继、可靠发布、幂等校验、重试与死信策略，或用户要求“完成后通知其他聚合/服务”。
  不适用于 Controller、Handler、Port 编排或 JPA 数据层细节实现；这些场景分别切到 `patra-hexagonal` 和 `patra-jpa`。
---

# Patra 事件驱动

在 Patra 中设计内部领域事件、事务后监听器、Outbox 和可靠消息发布时，优先用本技能判定模式和约束。

## Quick Start

1. 先读以下规则文件：
- `.claude/rules/layers/domain.md`
- `.claude/rules/layers/application.md`
- `.claude/rules/tech/error-handling.md`
2. 先判断是内部协作还是跨服务通信：
   - 同一服务内协作：优先 Spring 应用事件
   - 跨服务可靠投递：优先 Outbox + MQ
3. 需要内部事件模板时读取 `references/event-patterns.md`。
4. 需要 Outbox、中继、重试和租约细节时读取 `references/outbox-guide.md`。

## Workflow

1. 在 domain 层定义过去时命名的事件，只保留消费方真正需要的字段。
2. 内部事件在 app 层用监听器处理；事务提交后处理时使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`。
3. 需要独立事务时，再配 `@Transactional(propagation = REQUIRES_NEW)`。
4. 外部事件在同一业务事务内同时写入业务数据和 Outbox 记录。
5. 中继异步扫描 Outbox，先获取租约，再发布，再标记状态。
6. 为处理器和中继都补幂等、重试和失败可观测性。

## Quality Rules

- 不要把内部协作直接设计成 MQ，也不要把跨服务可靠投递简化成普通 `@EventListener`。
- 事务边界只能放在 Application 层。
- 事务回滚后不能已经把消息发出去。
- 监听器和中继不要吞异常；静默失败比显式失败更危险。
- 事件链必须线性可推导，避免 A -> B -> A 循环。

## Reference Map

- `references/event-patterns.md`：设计内部事件、处理器和链式事件时读取。
- `references/outbox-guide.md`：设计 Outbox 表、租约、中继、重试和幂等时读取。

## Switch Skills

- 创建 Controller、Handler、Gateway、Port 或决定分层落位：切到 `patra-hexagonal`
- 编写 Entity、Dao、Mapper、RepositoryAdapter 或排查 JPA 细节：切到 `patra-jpa`

## Validation

- 检查内部事件是否真的只在服务内协作。
- 检查是否使用了 `AFTER_COMMIT`、幂等检查和清晰的失败策略。
- 检查 Outbox 中继是否具备租约、重试、终态和告警。
- 检查是否存在吞异常或事件循环。
