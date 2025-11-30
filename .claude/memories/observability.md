# 可观测性规范

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│  应用 (patra-{service})                                      │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Micrometer MeterRegistry                            │    │
│  │  - Spring Batch 指标                                 │    │
│  │  - HikariCP 连接池                                   │    │
│  │  - HTTP 服务端指标                                   │    │
│  │  - 自定义业务指标 (@Timed/@Counted)                  │    │
│  └───────────────────────┬─────────────────────────────┘    │
│                          │ ← otelMeterRegistryBridge()       │
│  ┌───────────────────────▼─────────────────────────────┐    │
│  │  OTel Agent (Traces + Logs + Metrics)               │    │
│  └───────────────────────┬─────────────────────────────┘    │
└──────────────────────────┼──────────────────────────────────┘
                           │ OTLP (统一协议)
                           ↓
┌──────────────────────────────────────────────────────────────┐
│  OTel Collector → Tempo (Traces) / Prometheus / Loki (Logs) │
└──────────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────┐
│  Grafana                                                     │
└──────────────────────────────────────────────────────────────┘
```

## 核心原则

1. **统一遥测管道**：Traces/Metrics/Logs 全部通过 OTel Agent → OTLP → OTel Collector
2. **Micrometer Bridge**：Spring/Micrometer 指标通过 Agent 桥接导出到 OTel Collector
3. 自定义业务指标使用 Micrometer API（`MeterRegistry`、`@Timed`、`@Counted`）
4. 业务代码无需感知 OpenTelemetry

## OTel Agent 配置

关键配置（`docker/otel-agent/otel-dev.properties`）：

```properties
# Metrics 通过 OTLP 导出
otel.metrics.exporter=otlp

# 启用 Micrometer 桥接（Agent 2.0+ 默认禁用）
otel.instrumentation.micrometer.enabled=true
```

## Micrometer Bridge 机制

**问题**：OTel Agent 将 `OpenTelemetryMeterRegistry` 注册到 `Metrics.globalRegistry`，
但 Spring 使用 ApplicationContext 中的 MeterRegistry Bean。

**解决方案**：`MicrometerAutoConfiguration.otelMeterRegistryBridge()` 将 Agent 注入的
Registry 暴露为 Primary Bean，覆盖 Spring Boot Actuator 默认的 `SimpleMeterRegistry`。

## 层级职责

| 层级 | 可观测性职责 |
|------|-------------|
| Domain | 禁止任何可观测性代码 |
| Application | 可使用 Micrometer 记录业务指标 |
| Infrastructure | 可使用 Micrometer 记录技术指标 |
| Adapter | 可使用 SLF4J 记录请求日志 |

## 指标命名规范

所有指标由 OTel Collector 统一添加 `patra_` 前缀（通过 Prometheus exporter 的 `namespace: patra` 配置）：

- `patra_jvm_*` - JVM 指标
- `patra_http_server_*` - HTTP 服务端指标
- `patra_http_client_*` - HTTP 客户端指标
- `patra_db_*` - 数据库指标
- `patra_spring_batch_*` - Spring Batch 指标
- `patra_{module}_*` - 自定义业务指标

**注意**：Prometheus 会将 `.` 转换为 `_`，所以在 PromQL 查询时使用下划线。

## 公共标签

所有指标自动添加公共标签（由 `CommonTagsMeterFilter` 处理）：

- `application` - 服务名称
- `environment` - 环境（dev/prod）
- `region` - 区域（可选）
- `cluster` - 集群（可选）

## 禁止行为

1. 禁止直接使用 OpenTelemetry SDK（`io.opentelemetry.api.*`）
2. 禁止手动创建/管理 Span 生命周期
3. 禁止在 Domain 层添加任何可观测性代码
