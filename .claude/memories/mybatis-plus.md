# MyBatis-Plus 使用规范

1. 禁止在 Mapper 接口中使用 `@Select` 等注解编写 SQL，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML
2. 所有 DO 必须继承 `BaseDO`（包含雪花 ID 主键和 10 个审计字段）
