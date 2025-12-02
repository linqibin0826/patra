# 可观测性规范

## 核心原则

1. **统一遥测管道**：Traces/Metrics/Logs 全部通过 OTel Agent → OTLP → OTel Collector
2. **Micrometer Bridge**：Spring/Micrometer 指标通过 Agent 桥接导出到 OTel Collector
3. 自定义业务指标使用 Micrometer API（`MeterRegistry`、`@Timed`、`@Counted`）
4. 业务代码无需感知 OpenTelemetry

## 指标命名规范

所有指标由 OTel Collector 统一添加 `patra_` 前缀（通过 Prometheus exporter 的 `namespace: patra` 配置）：

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
