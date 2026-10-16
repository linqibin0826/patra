# patra-tool-batch

## 能力

给服务提供 Spring Batch 基础设施：`BatchAutoConfiguration` 装配 `JobRepository` 与 `JobOperatorHelper`；`BatchDataSourceAutoConfiguration` 在配了 `datasource.url` 时才建独立的 Batch 元数据库 HikariCP 数据源；`BatchSchemaAutoConfiguration` 在启动时建 Batch 元数据表；`BatchMetricsAutoConfiguration` 在有 `MeterRegistry` 时注册进度指标监听器。

## 配置（前缀 `patra.batch`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `table-prefix` | String | `BATCH_` | JobRepository 表前缀 |
| `chunk.default-size` | int | `5000` | 默认分块，最小 1 |
| `chunk.max-size` | int | `10000` | 允许的最大分块，最小 1 |
| `datasource.url` | String | 无 | JDBC URL；不配则用主数据源，不建独立数据源 |
| `datasource.username` | String | 无 | 用户名 |
| `datasource.password` | String | 无 | 密码 |
| `datasource.driver-class-name` | String | 无 | 驱动类，空则由 URL 推断 |
| `datasource.hikari.maximum-pool-size` | int | `5` | 最大连接数，最小 1 |
| `datasource.hikari.minimum-idle` | int | `2` | 最小空闲连接数，最小 0 |
| `datasource.hikari.connection-timeout` | long | `30000` | 取连接超时（毫秒） |
| `datasource.hikari.idle-timeout` | long | `600000` | 空闲连接回收时间（毫秒） |
| `datasource.hikari.max-lifetime` | long | `1800000` | 连接最长寿命（毫秒） |
| `schema.initialize` | boolean | `true` | 启动时是否建 Batch 元数据表 |
| `import-limit.max-records` | long | `-1` | 最多导入条数（调试用），小于等于 0 表示不限 |
| `metrics.enabled` | boolean | `true` | 是否注册进度指标监听器 |

## 怎么覆盖

```yaml
patra:
  batch:
    chunk:
      default-size: 2000
    datasource:
      url: jdbc:postgresql://localhost:5432/patra_batch
      username: patra
```

## 出哪些默认值

无。
