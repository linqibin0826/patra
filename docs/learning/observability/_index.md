---
title: 可观测性学习系列
date: 2025-11-28
tags: [observability, learning, tutorial]
---

# 可观测性学习系列

> 从零开始系统学习可观测性（Observability），掌握 OpenTelemetry + Grafana Stack 技术栈

## 学习路径

```mermaid
flowchart LR
    A["✓ 第一章：核心概念"] --> B["第二章：Metrics"]
    B --> C["第三章：Logs"]
    C --> D["第四章：Traces"]
    D --> E["第五章：告警"]
    E --> F["第六章：Grafana 可视化"]

    style A fill:#d4edda,stroke:#28a745
```

## 章节目录

| 章节 | 标题 | 状态 | 内容 |
|------|------|------|------|
| 01 | [[01-core-concepts\|核心概念]] | 已完成 | 可观测性定义、三大支柱、信号关联 |
| 02 | [[02-metrics\|Metrics（指标）]] | 待学习 | 指标类型、Micrometer、Prometheus、PromQL |
| 03 | [[03-logs\|Logs（日志）]] | 待学习 | 结构化日志、Loki、LogQL、日志与 Trace 关联 |
| 04 | [[04-traces\|Traces（链路追踪）]] | 待学习 | OpenTelemetry、Span、Context Propagation |
| 05 | [[05-alerting\|告警]] | 待学习 | Alertmanager、告警规则、通知配置 |
| 06 | [[06-grafana\|Grafana 可视化]] | 待学习 | 数据源配置、仪表盘设计、统一查询 |

## 技术栈

本系列基于以下技术栈：

- **采集层**：OpenTelemetry Java Agent + Micrometer
- **处理层**：OpenTelemetry Collector
- **存储层**：Prometheus（指标）+ Loki（日志）+ Tempo（链路）
- **展示层**：Grafana
- **告警层**：Alertmanager

## 相关资源

- 架构决策：[[decisions/ADR-005-adopt-opentelemetry-grafana-stack-for-observability|ADR-005 采用 OTel + Grafana Stack]]
- TIL 索引：[[til/2025/11/2025-11-28-observability-basics|可观测性基础 TIL]]（待创建）

## 外部资源

- [OpenTelemetry 官方文档](https://opentelemetry.io/docs/)
- [Grafana LGTM Stack](https://grafana.com/oss/lgtm-stack/)
- [Spring Boot 3 Observability](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3/)
