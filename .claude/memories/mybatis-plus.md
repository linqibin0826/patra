# MyBatis-Plus 使用规范

1. 禁止在 Mapper 接口中使用 `@Select` 等注解编写 SQL，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML
2. 所有 DO 必须继承 `BaseDO`（包含雪花 ID 主键和 10 个审计字段）
3. 所有 Mapper 必须继承 `PatraBaseMapper`（而非 `BaseMapper`），获得 `insertBatchSomeColumn` 批量插入能力
4. 数据量 > 100 条必须使用 `PatraBaseMapper.insertBatchSomeColumn()` 或 `BatchInsertHelper.batchInsert()` 批量插入，禁止循环调用 `insert()`
5. 禁止使用 `ServiceImpl.saveBatch()`（底层是循环 INSERT）和继承 `ServiceImpl`，RepositoryAdapter 应直接注入 Mapper
6. `insertBatchSomeColumn` 自动触发审计字段填充，无需手动设置 `createdAt`、`updatedAt`、`version`、`deleted`
