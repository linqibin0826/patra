---
title: 可观测性系统设计
type: design
status: draft
date: 2025-11-29
module: patra-spring-boot-starter-observability
related_adrs: [ADR-005, ADR-001]
related_learning: [learning/observability/_MOC]
tags:
  - design/infrastructure
  - tech/opentelemetry
  - tech/grafana
---

# 可观测性系统设计

> 基于 OpenTelemetry + Grafana Stack 的统一可观测性平台设计。
>
> 本设计实现 ADR-005 决策，构建统一的可观测性平台，覆盖 Metrics、Logs、Traces 三大支柱。

## 设计概览

```d2 width=900

direction: right

classes: {
  layer: {
    style: {
      border-radius: 8
      fill: "#f8fafc"
      stroke: "#64748b"
      stroke-width: 2
      font-color: "#1e293b"
      font-size: 20
    }
  }
  component: {
    style: {
      fill: "#3b82f6"
      stroke: "#1d4ed8"
      stroke-width: 2
      font-color: "#ffffff"
      font-size: 16
    }
  }
  storage: {
    shape: cylinder
    style: {
      fill: "#8b5cf6"
      stroke: "#6d28d9"
      stroke-width: 2
      font-color: "#ffffff"
      font-size: 16
    }
  }
  viz: {
    style: {
      fill: "#f97316"
      stroke: "#ea580c"
      stroke-width: 2
      font-color: "#ffffff"
      font-size: 16
    }
  }
  processor: {
    shape: hexagon
    style: {
      fill: "#10b981"
      stroke: "#059669"
      stroke-width: 2
      font-color: "#ffffff"
      font-size: 16
    }
  }
}

# 采集层
collection: 采集层 {
  class: layer

  app: Spring Boot App {class: component}
  agent: OTel Agent {class: component}
  micrometer: Micrometer {class: component}
  logback: Logback OTLP {class: component}
}

# 处理层
processing: 处理层 {
  class: layer

  collector: OTel Collector {class: processor}
}

# 存储层
storage: 存储层 {
  class: layer

  prometheus: Prometheus {class: storage}
  loki: Loki {class: storage}
  tempo: Tempo {class: storage}
}

# 展示层
visualization: 展示层 {
  class: layer

  grafana: Grafana {class: viz}
  alertmanager: Alertmanager {class: component}
}

# 连接
collection.agent -> processing.collector: OTLP/Traces {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
collection.micrometer -> processing.collector: OTLP/Metrics {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
collection.logback -> processing.collector: OTLP/Logs {
  style.stroke: "#64748b"
  style.stroke-width: 2
}

processing.collector -> storage.prometheus: Remote Write {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
processing.collector -> storage.loki: Push {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
processing.collector -> storage.tempo: OTLP {
  style.stroke: "#64748b"
  style.stroke-width: 2
}

storage.prometheus -> visualization.grafana {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
storage.loki -> visualization.grafana {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
storage.tempo -> visualization.grafana {
  style.stroke: "#64748b"
  style.stroke-width: 2
}

visualization.grafana -> visualization.alertmanager: Alert Rules {
  style.stroke: "#64748b"
  style.stroke-width: 2
}
```

## 文档导航

| 章节                                            | 内容                          | 状态 |
| --------------------------------------------- | --------------------------- | --- |
| [[01-overview\|01. 概述]]                       | 问题陈述、目标、术语表                 | 草稿 |
| [[02-architecture\|02. 架构设计]]                 | 整体架构、数据流、时序图                | 草稿 |
| [[03-starter-module\|03. Starter 模块]]         | 模块重构、配置属性、自动配置              | 草稿 |
| [[04-otel-integration\|04. OTel 集成]]          | Agent 配置、Bridge、日志集成        | 草稿 |
| [[05-infrastructure\|05. 基础设施]]               | Docker Compose、Collector 配置 | 草稿 |
| [[06-grafana-visualization\|06. Grafana 可视化]] | 仪表盘、信号关联、告警规则               | 草稿 |
| [[07-implementation-roadmap\|07. 实现路线图]]      | 分阶段计划、测试策略、风险               | 草稿 |
| [[08-version-matrix\|08. 版本矩阵]]               | 组件版本、兼容性、升级策略               | 草稿 |

## 技术决策摘要

| 决策点 | 选择 | 理由 |
|--------|------|------|
| **采集方式** | OTel Java Agent | 零代码侵入，自动覆盖框架 |
| **Micrometer** | 保留 + Bridge | Spring Boot 原生支持，复用现有代码 |
| **日志采集** | OTLP Appender | 统一协议，减少组件 |
| **Collector 模式** | Gateway | 单实例简化部署 |
| **采样策略** | Tail Sampling | 保证错误/慢请求不丢失 |
| **仪表盘配置** | Provisioning | 配置即代码，版本控制 |

## 技术栈

> 详细版本信息和兼容性说明请参见 [[08-version-matrix|版本矩阵]]

| 层级 | 组件 | 版本 | 用途 |
|------|------|------|------|
| **采集** | OpenTelemetry Java Agent | 2.22.0 | Traces/Metrics 自动采集 |
| **采集** | Micrometer | 1.15.5 | Spring Boot 原生指标 API |
| **采集** | Logback OTLP Appender | 2.22.0-alpha | 结构化日志输出 |
| **处理** | OpenTelemetry Collector | 0.140.1 | 接收、处理、导出 |
| **存储** | Prometheus | 3.7.3 | 指标时序存储 |
| **存储** | Loki | 3.6.2 | 日志聚合存储 |
| **存储** | Tempo | 2.9.0 | 分布式链路存储 |
| **展示** | Grafana | 12.3.1 | 统一可视化 |
| **告警** | Alertmanager | 0.29.0 | 告警路由通知 |

## 相关资源

### 架构决策

- [[decisions/ADR-005-adopt-opentelemetry-grafana-stack-for-observability|ADR-005: 采用 OTel + Grafana Stack]]

### 学习材料

- [[learning/observability/_MOC|可观测性学习系列]]
  - [[learning/observability/01-core-concepts|核心概念]]
  - [[learning/observability/02-metrics|Metrics]]
  - [[learning/observability/03-logs|Logs]]
  - [[learning/observability/04-traces|Traces]]
  - [[learning/observability/05-alerting|告警]]
  - [[learning/observability/06-grafana|Grafana]]

### 外部资源

- [OpenTelemetry 官方文档](https://opentelemetry.io/docs/)
- [Grafana LGTM Stack](https://grafana.com/oss/lgtm-stack/)
- [Spring Boot 3 Observability](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3/)
