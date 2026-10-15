# patra-tool-test

## 能力

给测试代码用的支持模块，只以 `testImplementation` 引入。自动配置 `TestAutoConfiguration` 在类路径有 Micrometer 时补一个内存 `SimpleMeterRegistry`，让带指标的 bean 能在测试上下文里装配。

另外带两类测试工具：Testcontainers 初始化器（`PostgreSQLContainerInitializer`、`RedisContainerInitializer`、`MinIOContainerInitializer`，在 `container/initializer/`）和 ArchUnit 规则（`HexagonalArchitectureRules` 六边形架构约束、`TestingRules` 测试约定，在 `archunit/`）。

## 配置（前缀 `patra.test`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |

## 怎么覆盖

```yaml
patra:
  test:
    enabled: false
```

## 出哪些默认值

无。
