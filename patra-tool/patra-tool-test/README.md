# patra-tool-test

## 能力

给测试代码用的支持模块，只以 `testImplementation` 引入。自动配置 `TestAutoConfiguration` 在类路径有 Micrometer 时补一个内存 `SimpleMeterRegistry`，让带指标的 bean 能在测试上下文里装配。

另外带两类测试工具：Testcontainers 初始化器（`PostgreSQLContainerInitializer`、`RedisContainerInitializer`、`MinIOContainerInitializer`，在 `container/initializer/`）和 ArchUnit 规则（`HexagonalArchitectureRules` 六边形架构约束、`TestingRules` 测试约定、`SubdomainArchitectureRules` 子域边界，在 `archunit/`）。

`SubdomainArchitectureRules` 给每层再按子域分包的服务用（`{根包}.{层}.{子域}`，目前是 interaction）：层下面只能有声明过的子域；子域之间无环；跨子域只能用对方的公开包（`domain.{子域}.port.read`、`domain.{子域}.model.read`，底座子域另加 `port.gateway`、`model.vo`、`model.enums`）；底座不依赖任何子域。boot 的启动类与装配不在五个层下，不受管。层规则里的子包路径（`model.aggregate`、`event`、`usecase`、`persistence.entity`）允许中间隔着子域。

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
