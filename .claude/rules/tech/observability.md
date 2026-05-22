---
paths: patra-*/*-app/**/*.java, patra-*/*-infra/**/*.java, patra-*/*-adapter/**/*.java
---

# 可观测性规范

## 核心原则

1. **统一遥测管道**：Traces/Metrics/Logs 全部通过 OTel Agent → OTLP → OTel Collector
2. **Micrometer Bridge**：Spring/Micrometer 指标通过 Agent 桥接导出到 OTel Collector
3. 自定义业务指标使用 Micrometer API（`MeterRegistry`、`@Timed`、`@Counted`）
4. 业务代码无需感知 OpenTelemetry

## 公共标签

所有指标自动添加公共标签（由 `CommonTagsMeterFilter` 处理）：`application`、`environment`、`region`、`cluster`

## 禁止行为

1. 禁止直接使用 OpenTelemetry SDK（`io.opentelemetry.api.*`）
2. 禁止手动创建/管理 Span 生命周期
