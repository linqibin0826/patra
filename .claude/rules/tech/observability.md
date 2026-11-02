---
paths: patra-api/*/*-app/**/*.java, patra-api/*/*-infra/**/*.java, patra-api/*/*-adapter/**/*.java
---

# 可观测性规范

## 核心原则

1. **指标**：Micrometer 的 `OtlpMeterRegistry` → OTLP → OTel Collector → Prometheus，依赖与导出地址由 `patra-tool-observability` 统一给，服务不再单独加
2. **链路与日志**：本机运行挂 OTel Agent（只导出链路与日志，指标关掉，免得重复）；容器里不挂 Agent
3. 自定义业务指标使用 Micrometer API（`MeterRegistry`、`@Timed`、`@Counted`）
4. 业务代码无需感知 OpenTelemetry

## 公共标签

所有指标自动添加公共标签（由 `CommonTagsMeterFilter` 处理）：`application`、`environment`、`region`、`cluster`

## 禁止行为

1. 禁止直接使用 OpenTelemetry SDK（`io.opentelemetry.api.*`）
2. 禁止手动创建/管理 Span 生命周期
3. 禁止给 Timer 打开 `percentiles-histogram`，除非面板真要分位数；要就只开那一个指标，并估算 Prometheus 序列数
