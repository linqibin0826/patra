---
paths: patra-api/*/*-infra/**/*.java, patra-tool/patra-tool-jpa/**/*.java
---

# JPA 使用规范

只适用于用 JPA 的服务（registry、catalog、identity）。表结构、审计列、软删除见 [database.md](database.md)。

## 基类选择

| 基类 | 适用场景 |
|------|----------|
| **BaseJpaEntity** | 主表：操作人、IP、备注、乐观锁 |
| **ValueObjectJpaEntity** | 其他表：只有 `id`，实现 `Persistable`，新行直接插入 |

两个基类都不映射 `created_at` / `updated_at`（数据库填）。查询要用时，实体自己加只读字段（`insertable = false, updatable = false`），不用 `@Generated` / `@CreationTimestamp`。

主表的 `created_by` / `updated_by` 由审计按当前用户填，没有当前用户时写空（`UpdatedByResetListener` 在更新前先清空），实体和映射器不写这两列。

## 核心规范

1. **命名**：`{Name}Entity`、`{Name}Dao`、`{Name}JpaMapper`
2. **转换**：Entity ↔ Aggregate 使用 MapStruct
3. **ID**：使用 `SnowflakeIdGenerator.getId()` 预分配
4. **事务**：infra 不划定事务边界，只允许 `@Transactional(propagation = MANDATORY)` 守卫，详见 [transaction.md](transaction.md)
5. **批量**：大批量（> 500 条）需 `flush()` + `clear()`
6. **不经实体的写入**：只能是 Dao 上的 `@Query`，带 `@Modifying(flushAutomatically = true, clearAutomatically = true)`；写主表时显式 `SET updated_by`，取当前用户、系统写入写空（批量语句不触发审计）

## 软删除

用普通字段 `deletedAt` 映射 `deleted_at`，查询方法自己带「未删除」条件（`...AndDeletedAtIsNull`）。不用 `@SoftDelete`：它会把需要读已删行的查询逼成原生 SQL；去掉它之后，JPQL 批量删除会变成物理删除，要改写成 `UPDATE … SET deleted_at`。
