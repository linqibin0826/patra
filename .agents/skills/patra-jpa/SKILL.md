---
name: "patra-jpa"
description: "Patra JPA 数据层实现指南。创建或修改 Entity、Dao、JpaMapper、RepositoryAdapter、ReadAdapter 或排查 JPA 持久化问题时使用。"
---

# Patra JPA 数据层开发指南

在 Patra 的 infra 层实现数据持久化时，按此指南创建和组织 JPA 相关组件。

基本约束（基类选择、命名规范）已在 `rules/tech/jpa.md` 中自动加载，本技能聚焦于**实操模板和决策指导**。

## 四步创建流程

```
1. Entity（JPA 实体）
   └── 继承正确的基类，定义表结构
2. Dao（Spring Data Repository）
   └── 继承 JpaRepository，添加查询方法
3. JpaMapper（MapStruct 映射器）
   └── Entity ↔ Domain Aggregate 转换
4. RepositoryAdapter（Port 实现）
   └── 实现 Domain 层的 Repository 接口
```

## 基类选择决策树

```
这个实体是什么角色？
│
├── 聚合根（独立生命周期）
│   ├── 需要软删除？ → SoftDeletableJpaEntity
│   └── 不需要 → BaseJpaEntity
│
├── 子实体（有独立更新语义）
│   ├── 需要软删除？ → SoftDeletableChildJpaEntity
│   └── 不需要 → ChildJpaEntity
│
└── 值对象表（DELETE/INSERT 模式）
    └── ValueObjectJpaEntity
```

| 基类 | 字段 | 适用场景 |
|------|------|----------|
| `BaseJpaEntity` | id, version, createdAt, updatedAt 等 10 字段 | 聚合根 |
| `SoftDeletableJpaEntity` | 上述 + deletedAt | 需要软删除的聚合根 |
| `ChildJpaEntity` | id, version, createdAt, updatedAt 4 字段 | 有独立更新的子实体 |
| `SoftDeletableChildJpaEntity` | 上述 + deletedAt | 需要软删除的子实体 |
| `ValueObjectJpaEntity` | id 1 字段 | DELETE/INSERT 模式值对象 |

## 查询方式选择

| 场景 | 方式 | 示例 |
|------|------|------|
| 简单查询 | 方法命名约定 | `findByIssnL(String issnL)` |
| 复杂查询 | `@Query` + JPQL | `@Query("SELECT v FROM VenueEntity v WHERE ...")` |
| 动态条件 | `Specification` | 多条件可选组合 |
| 原生 SQL | `@Query(nativeQuery = true)` | 特殊优化场景 |

## 完整实现模板

详细的代码模板和高级模式，请参考 [jpa-patterns.md](resources/jpa-patterns.md)，包括：

- Entity 定义（JSON 列、嵌入式值对象、索引）
- Dao 查询方法
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
| ID 为 null | 未调用 `assignIdIfMissing()` | 在 save 前调用雪花 ID 预分配 |

## Codex 工具使用说明

原 Claude `allowed-tools` 已改写为 Codex 操作建议，不是权限边界。使用本技能时优先读取 JPA 规则与相关代码、用 `rg`/文件检索定位 Entity/Dao/Mapper/Adapter，再通过 `apply_patch` 做小范围修改，并在必要时运行 Gradle 验证命令。
