---
name: patra-jpa
description: |
  Patra JPA 数据层实现指南。在 infra 层创建或修改 Entity、Dao、JpaMapper、RepositoryAdapter、ReadAdapter，或排查 JPA 映射问题时使用。
  触发场景：创建持久化模型、编写查询方法、实现 MapStruct 映射器、落地 RepositoryAdapter 或 ReadAdapter，以及处理批量保存、N+1、乐观锁、AttributeConverter、软删除和 ID 分配等问题。
  不适用于 Controller、Handler、Port 编排或事件链设计；这些场景分别切到 `patra-hexagonal` 和 `patra-events`。
---

# Patra JPA 数据层

在 Patra 的 infra 层实现持久化模型、查询、映射和仓储适配器时，用本技能控制结构与边界。

## Quick Start

1. 先读以下规则文件：
- `.claude/rules/layers/infrastructure.md`
- `.claude/rules/tech/jpa.md`
- `.claude/rules/code-style.md`
2. 先判断实体角色，再选基类：
   - 聚合根：`BaseJpaEntity`
   - 软删除聚合根：`SoftDeletableJpaEntity`
   - 子实体：`ChildJpaEntity`
   - 软删除子实体：`SoftDeletableChildJpaEntity`
   - DELETE/INSERT 值对象表：`ValueObjectJpaEntity`
3. 按固定顺序落地：`Entity -> Dao -> JpaMapper -> RepositoryAdapter/ReadAdapter`。
4. 需要完整模板或高级模式时，再读 `references/jpa-patterns.md`。

## Workflow

1. 在 `Entity` 中完成字段映射、索引、JSON 列、关联关系和基类选择。
2. 在 `Dao` 中优先用方法命名约定，复杂查询再用 `@Query` 或 `Specification`。
3. 在 `JpaMapper` 中统一维护 `Entity <-> Aggregate` 转换，不要散落手写映射。
4. 在 `RepositoryAdapter` / `ReadAdapter` 中实现 domain 接口，只暴露真正需要的能力。
5. 遇到事务编排、端口划分或事件链需求时，切到对应 skill，不要在 infra 层偷做业务编排。

## Quality Rules

- 事务边界不放在 infra 层。
- `Entity <-> Aggregate` 转换统一用 MapStruct。
- 大批量保存要显式 `flush()` + `clear()` 控制一级缓存。
- ID 预分配、乐观锁和软删除语义必须遵循 Patra 统一基类约定。
- 只有确有必要时才使用原生 SQL。

## Common Risks

- `OptimisticLockException`：并发更新策略不清晰。
- N+1：懒加载链路失控，优先考虑 `@EntityGraph` 或 `JOIN FETCH`。
- 批量保存 OOM：一级缓存积压。
- ID 为 `null`：保存前没有分配 ID。
- Mapper 的 `@AfterMapping` 吞异常：会造成半转换半失败。

## Reference Map

- `references/jpa-patterns.md`：需要完整模板、批量保存、AttributeConverter、ReadAdapter 和 N+1 细节时读取。

## Switch Skills

- 决定分层、组件职责、端口命名或 Starter：切到 `patra-hexagonal`
- 设计领域事件、Outbox 和可靠消息发布：切到 `patra-events`

## Validation

- 检查是否把业务编排或事务控制写进了 infra。
- 检查是否仍有手写大段映射逻辑或裸露的 Entity 泄漏。
- 检查查询实现是否存在明显 N+1、批量写入和 ID 分配风险。
- 检查 RepositoryAdapter / ReadAdapter 是否只实现 domain 所需能力。
