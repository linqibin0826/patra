---
paths: patra-*/*-infra/**/*.java, patra-spring-boot-starter-jpa/**/*.java
---

# JPA 使用规范

## 基类选择

| 基类 | 适用场景 |
|------|----------|
| **BaseJpaEntity** | 聚合根（完整审计） |
| **SoftDeletableJpaEntity** | 需要软删除的聚合根 |
| **ChildJpaEntity** | 有独立更新语义的子实体 |
| **SoftDeletableChildJpaEntity** | 需要软删除的子实体 |
| **ValueObjectJpaEntity** | 值对象表（DELETE/INSERT 模式） |

## 核心规范

1. **命名**：`{Name}Entity`、`{Name}Dao`、`{Name}JpaMapper`
2. **转换**：Entity ↔ Aggregate 使用 MapStruct
3. **ID**：使用 `SnowflakeIdGenerator.getId()` 预分配
4. **事务**：只在 Application 层使用 `@Transactional`
5. **批量**：大批量（> 500 条）需 `flush()` + `clear()`

## 软删除

使用 Hibernate `@SoftDelete(strategy = TIMESTAMP)`，直接调用 `repository.delete()` 即可。

- `deleted_at` 列由 Hibernate 自动管理，不是实体字段
- 领域模型通过状态枚举标记删除，不维护 `deletedAt`
- 查询已删除记录需用 Native Query
