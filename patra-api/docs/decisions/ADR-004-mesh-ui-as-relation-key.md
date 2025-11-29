---
type: adr
adr_id: 4
date: 2025-11-27
status: accepted
date_decided: 2025-11-27
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - domain/mesh
  - tech/data-model
---

# ADR-004: MeSH 子表使用原生 UI 作为关联键

## 状态

**accepted**

## 背景

MeSH 数据模型中，主题词（Descriptor）与子表（TreeNumber、Concept、EntryTerm、ConceptRelation、EntryCombination）之间需要建立关联关系。原设计使用数据库自增 ID（`Long descriptorId`）作为关联键，导致以下问题：

1. **导入流程复杂**：批量导入时，需要先插入主表获取自增 ID，再将 ID 分配给子表记录
2. **额外查询开销**：若采用先全量插入主表再插入子表的方式，需要额外的 UI→ID 映射查询
3. **数据可移植性差**：自增 ID 是数据库实例级别的，跨环境迁移时 ID 会变化

MeSH 原生 UI（如 D000001、M000001）是 NLM 官方定义的全局唯一标识符，跨版本稳定，天然适合作为关联键。

## 决策

将 MeSH 子表的关联键从数据库自增 ID（`Long descriptorId`）改为 MeSH 原生 UI 标识符（`String descriptorUi`）。

**涉及范围**：
- Domain 层：`MeshConcept`、`MeshEntryTerm`、`MeshTreeNumber` 实体的 `descriptorId` 字段改为 `descriptorUi`（类型 `MeshUI`）
- Infra 层：6 个 DO 类的 `descriptorId: Long` 改为 `descriptorUi: String`
- 数据库：6 张子表的 `descriptor_id BIGINT` 列改为 `descriptor_ui VARCHAR(10)`
- 同步更新：`PublicationMeshDO` 的 `qualifierId` 也改为 `qualifierUi`

## 后果

### 正面影响

- **简化导入流程**：无需先查询主表获取自增 ID，直接使用源数据中的 UI
- **消除 UI→ID 映射**：ItemWriter 中直接从聚合根获取 UI，无需额外的 ID 分配逻辑
- **数据自描述**：关联键即业务标识符，数据可读性更强
- **跨环境一致**：无论在哪个数据库实例，相同的 MeSH 记录关联键相同
- **符合 DDD 原则**：使用领域概念（MeshUI）而非技术概念（自增 ID）

### 负面影响

- **索引性能略降**：`VARCHAR(10)` 索引比 `BIGINT` 索引略慢（约 5-10%），但在当前数据量下可忽略
- **存储空间略增**：每条记录增加约 2 字节（10 字节 vs 8 字节），6 张表约 55 万条记录，总增加约 1MB

### 风险

- 无显著风险。MeSH UI 是稳定的业务标识符，NLM 保证其唯一性和持久性。

## 替代方案

### 方案 A：保留自增 ID + 增加 UI 索引

保持 `descriptorId` 作为关联键，同时在主表上建立 UI 的唯一索引。

**优点**：
- 关联查询使用整数比较，性能最优
- 符合传统关系型数据库设计范式

**缺点**：
- 导入时仍需查询 UI→ID 映射
- 两套标识符增加维护复杂度

### 方案 B：使用联合主键

子表使用 `(descriptorUi, 业务字段)` 作为联合主键，不设自增 ID。

**优点**：
- 完全消除自增 ID
- 数据结构最简洁

**缺点**：
- 与项目 `BaseDO` 设计不兼容（要求雪花 ID 主键）
- 联合主键在 MyBatis-Plus 中使用复杂

## 参考资料

- [MeSH Unique Identifier (UI) - NLM](https://www.nlm.nih.gov/mesh/intro_record_types.html)
- [Domain-Driven Design: Using Natural Keys](https://martinfowler.com/bliki/NaturalKey.html)
