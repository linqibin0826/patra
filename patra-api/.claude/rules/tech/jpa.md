---
paths: patra-*/*-infra/**/*.java, patra-spring-boot-starter-jpa/**/*.java
---

# JPA 使用规范

1. JPA Entity 继承规则：
   - **聚合根实体**：必须继承 `BaseJpaEntity`（雪花 ID、审计字段、乐观锁）或 `SoftDeletableJpaEntity`（额外支持软删除）
   - **子实体**（随聚合根一起保存的 1:N 关系表）：可使用精简审计字段（`id`, `version`, `created_at`, `updated_at`），无需继承基类
2. 命名规则：`{Name}Entity`、`{Name}Dao`、`{Name}JpaMapper`、`{Type}AttributeConverter`
3. Entity ↔ Aggregate 转换使用 MapStruct，禁止手动编写
4. 批量保存使用 `saveAll()`，大批量（> 500 条）需手动 `flush()` + `clear()` 防止内存溢出
5. ID 使用 `SnowflakeIdGenerator.getId()` 预分配，禁止数据库自增
6. 事务只在 Application 层使用 `@Transactional`，Infrastructure 层禁止声明事务
7. 软删除仅对聚合根和被引用的配置数据启用，继承 `SoftDeletableJpaEntity`；外部数据快照等子表使用物理删除
8. 数据库连接 URL 必须包含 `rewriteBatchedStatements=true`
