---
paths: patra-*/*-infra/**/*.java, patra-spring-boot-starter-jpa/**/*.java
---

# JPA 使用规范

1. 所有 JPA Entity 必须继承 `BaseJpaEntity`（包含雪花 ID、审计字段、乐观锁、软删除）
2. 命名规则：`{Name}Entity`、`{Name}Dao`、`{Name}JpaMapper`、`{Type}AttributeConverter`
3. Entity ↔ Aggregate 转换使用 MapStruct，禁止手动编写
4. 批量保存使用 `saveAll()`，大批量（> 500 条）需手动 `flush()` + `clear()` 防止内存溢出
5. ID 使用 `SnowflakeIdGenerator.getId()` 预分配，禁止数据库自增
6. 事务只在 Application 层使用 `@Transactional`，Infrastructure 层禁止声明事务
7. 软删除使用 `@SQLRestriction("deleted_at IS NULL")`，禁止物理删除
8. 数据库连接 URL 必须包含 `rewriteBatchedStatements=true`
