---
name: patra-jpa
description: |
  Patra JPA 数据层实现指南（只适用于 registry、catalog、identity 三个 JPA 服务）。当用户在 infra 层处理数据持久化相关代码时使用此技能。
  触发场景：创建或修改 Entity（基类选择、字段映射、JSON 列、软删除）、实现 Dao 查询方法、
  编写 JpaMapper（含 @AfterMapping 子实体转换）、实现 RepositoryAdapter 或 ReadAdapter、
  解决 JPA 问题（N+1、批量保存 OOM、AttributeConverter）。
  不适用于：Flyway 迁移脚本、Controller/Handler 创建（用 patra-hexagonal）、
  领域事件与 Outbox（用 patra-events）、Port 接口定义、interaction / notification / analytics（它们用 JdbcClient，不出现 JPA）。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash
---

# Patra JPA 数据层开发指南

在 Patra 的 infra 层实现数据持久化时，按此指南创建和组织 JPA 相关组件。

基本约束已在 `.claude/rules/tech/jpa.md`（基类、写法）与 `.claude/rules/tech/database.md`（表结构、审计列、软删除）中自动加载，本技能聚焦于**实操模板和决策指导**。表结构由 Flyway 脚本定义，实体只做映射：索引、唯一约束、列类型都写在迁移脚本里，不靠实体注解。

## 四步创建流程

```
1. Entity（JPA 实体）
   └── 按表的档位选基类，映射业务列
2. Dao（Spring Data Repository）
   └── 继承 JpaRepository，添加查询方法
3. JpaMapper（MapStruct 映射器）
   └── Entity ↔ Domain Aggregate 转换
4. RepositoryAdapter（Port 实现）
   └── 实现 Domain 层的 Repository 接口
```

## 基类选择

按表在 `db/tables.yml` 里登记的档位选：

```
这张表是哪一档？
│
├── 主表（有自己的生命周期、会被按行修改、「谁改的」有意义）→ BaseJpaEntity
│
└── 其他表（子表、关联表、明细、快照……）→ ValueObjectJpaEntity
```

| 基类 | 映射的列 | 说明 |
|------|----------|------|
| `BaseJpaEntity` | `id`、`created_by`、`updated_by`、`ip_address`、`record_remarks`、`version` | 操作人由 Spring Data 审计填（系统写入为空），`version` 是乐观锁 |
| `ValueObjectJpaEntity` | `id` | 实现 `Persistable`，新行直接插入、不先查 |

两个基类都不映射 `created_at` / `updated_at`：数据库触发器填，应用写了也被忽略。查询要用时实体自己加只读字段（`insertable = false, updatable = false`），不用 `@Generated` / `@CreationTimestamp`（会让批量插入退化成逐条执行）。

## 软删除

表有 `deleted_at` 列时，实体用普通字段 `deletedAt` 映射，查询方法自己带「未删除」条件（`findByIdAndDeletedAtIsNull`）。删除就是给 `deletedAt` 赋值后保存，或用 Dao 上的 `UPDATE … SET deleted_at`。不用 `@SoftDelete`。

## 查询与写入方式选择

| 场景 | 方式 | 示例 |
|------|------|------|
| 简单查询 | 方法命名约定 | `findByIssnLAndDeletedAtIsNull(String issnL)` |
| 复杂查询 | `@Query` + JPQL | `@Query("SELECT v FROM VenueEntity v WHERE ...")` |
| 动态条件 | `Specification` | 多条件可选组合 |
| 原生 SQL 查询 | `@Query(nativeQuery = true)` | 特殊优化场景 |
| 不经实体的写入 | Dao 上的 `@Query` + `@Modifying(flushAutomatically = true, clearAutomatically = true)` | 批量改状态、软删除；写主表时显式 `SET updated_by` |

写侧不用 `JdbcClient` / `JdbcTemplate`（ArchUnit 的写侧技术规则会拦）。

## 完整实现模板

详细的代码模板和高级模式，请参考 [jpa-patterns.md](resources/jpa-patterns.md)，包括：

- Entity 定义（JSON 列、嵌入式值对象、只读审计时间、软删除字段）
- Dao 查询方法与不经实体的写入
- MapStruct Mapper（含 @AfterMapping）
- RepositoryAdapter 完整实现
- ReadAdapter（CQRS 读端）实现
- 批量操作（flush + clear）
- 枚举 AttributeConverter
- N+1 查询解决（@EntityGraph / JOIN FETCH）

## 常见陷阱

| 问题 | 原因 | 解决 |
|------|------|------|
| `OptimisticLockException` | 并发更新 | 重试策略或业务异常 |
| N+1 查询 | 懒加载循环触发 | `@EntityGraph` 或 `JOIN FETCH` |
| 批量保存 OOM | 一级缓存堆积 | 每 500 条 `flush()` + `clear()` |
| ID 为 null | 保存前没预分配 | `entity.setId(SnowflakeIdGenerator.getId())` |
| 原生 `@Modifying` 语句读不到刚改的实体 | 原生语句不触发 Hibernate 自动落库 | `@Modifying` 带上 `flushAutomatically` 与 `clearAutomatically` |
| 查出了已删除的行 | 查询方法漏了未删除条件 | 方法名带 `AndDeletedAtIsNull`，JPQL 写 `deletedAt IS NULL` |
