# patra-tool-observability

## 能力

给服务提供统一的可观测性。`ObservabilityAutoConfiguration` 装配 `ObservationRegistry` 与 `@Observed` 支持；`ObservabilityMicrometerAutoConfiguration` 桥接 OTel Agent 的 `MeterRegistry`，并提供高基数标签过滤与公共标签两个 `MeterFilter`；`ObservabilityErrorAutoConfiguration` 在类路径有 patra-tool-web 时给错误解析管道加观测拦截器。模块还自带 logback 配置 `src/main/resources/dev/patra/tool/observability/logback-patra.xml` 与 traceId/spanId 转换器。

## 配置（前缀 `patra.observability`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `application-name` | String | 无 | 指标公共标签 application，空则用 `spring.application.name` |
| `environment` | String | 无 | 环境标签 |
| `region` | String | 无 | 区域标签 |
| `cluster` | String | `default` | 集群标签 |
| `metrics.enabled` | boolean | `true` | 是否注册 MeterFilter |
| `metrics.common-tags.<tag>` | Map<String, String> | 空 | 附加到所有指标的公共标签 |

## 怎么覆盖

```yaml
patra:
  observability:
    environment: dev
    metrics:
      common-tags:
        team: patra
```

## 出哪些默认值

由 `ObservabilityDefaultsPostProcessor` 以 `defaultProperties` 并入，服务 yml、Nacos、环境变量都能覆盖。

| 键 | 默认值 |
|---|---|
| `logging.file.path` | `${PATRA_LOG_DIR:logs}` |
| `logging.level.dev.patra` | `DEBUG` |
| `logging.config` | `classpath:dev/patra/tool/observability/logback-patra.xml` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` |
| `management.endpoint.health.show-details` | `when-authorized` |
| `management.metrics.tags.application` | `${spring.application.name}` |
