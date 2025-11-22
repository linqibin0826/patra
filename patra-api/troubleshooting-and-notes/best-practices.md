# 最佳实践

1. 禁止在 Mapper 接口中使用 `@Select` 等注解编写 SQL，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML
2. `patra-{service}-api` 模块仅定义服务间调用的接口和 DTO（不包含 Controller）
