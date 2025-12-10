# MyBatis-Plus 使用规范

1. 禁止在 Mapper 接口中使用 `@Select` 等注解编写 SQL，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML
2. 所有 DO 必须继承 `BaseDO`（包含雪花 ID 主键和 10 个审计字段）
3. 所有 Mapper 必须继承 `BaseMapper`
4. 数据量 > 100 条必须使用 `Db.saveBatch()` 批量插入，禁止循环调用 `insert()`
5. 禁止使用 `ServiceImpl.saveBatch()`（底层是循环 INSERT）和继承 `ServiceImpl`
6. `Db.saveBatch()` 自动触发 ID 回填和审计字段填充，无需手动设置
7. 数据库连接 URL 必须包含 `rewriteBatchedStatements=true`
8. `Db.saveBatch()` 会参与已存在的 Spring 事务，Application 层需用 `@Transactional` 或 `TransactionTemplate` 包裹以确保原子性
