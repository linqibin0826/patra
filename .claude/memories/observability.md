# 可观测性规范

## 核心原则

1. Metrics/Traces/Logs 通过 OpenTelemetry Java Agent 导出，业务代码无需感知
2. 自定义业务指标使用 Micrometer API（`MeterRegistry`、`@Timed`、`@Counted`）
3. 
## 层级职责

| 层级 | 可观测性职责 |
|------|-------------|
| Domain | 禁止任何可观测性代码 |
| Application | 可使用 Micrometer 记录业务指标 |
| Infrastructure | 可使用 Micrometer 记录技术指标 |
| Adapter | 可使用 SLF4J 记录请求日志 |

## 禁止行为

1. 禁止直接使用 OpenTelemetry SDK（`io.opentelemetry.api.*`）
2. 禁止手动创建/管理 Span 生命周期
