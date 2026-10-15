# patra-tool-cqrs

## 能力

给服务提供命令总线：`CommandBus`、`CommandHandler`、`CommandInterceptor` 三个接口，默认实现 `SimpleCommandBus`（收集容器里全部处理器与拦截器，`handleAsync` 走专用线程池）。内置日志、追踪、指标三个拦截器，追踪和指标分别要求容器里有 `ObservationRegistry` 和 `MeterRegistry`。自动配置类是 `CqrsAutoConfiguration`。

## 配置（前缀 `patra.cqrs`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关，关掉后本模块不注册任何 bean |
| `async.core-pool-size` | int | `4` | 核心线程数 |
| `async.max-pool-size` | int | `16` | 最大线程数 |
| `async.queue-capacity` | int | `100` | 队列容量 |
| `async.thread-name-prefix` | String | `cmd-bus-` | 线程名前缀 |
| `interceptors.logging` | boolean | `true` | 日志拦截器 |
| `interceptors.tracing` | boolean | `true` | 追踪拦截器（还要类路径上有 Observation 且容器里有 `ObservationRegistry`） |
| `interceptors.metrics` | boolean | `true` | 指标拦截器（还要容器里有 `MeterRegistry`） |

## 怎么覆盖

```yaml
patra:
  cqrs:
    async:
      core-pool-size: 8
    interceptors:
      metrics: false
```

## 出哪些默认值

无。
