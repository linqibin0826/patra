---
type: adr
adr_id: 1
date: 2025-11-26
status: accepted
date_decided: 2025-11-26
deciders: [Qibin Lin]
technical_debt: none
related_designs: [designs/observability/_MOC]
tags:
  - decision/architecture
  - tech/spring-batch
  - tech/observability
---

# ADR-001: Batch 可观测性迁移至 Spring 原生支持

## 状态

**accepted**

## 背景

自定义 `BatchObservationJobListener` 需要用户手动注入到每个 Job 定义中，使用繁琐且容易遗漏。Spring Batch 5+ 已提供原生可观测性支持，可自动为 Job 和 Step 创建 trace/span。

## 决策

我们将使用 Spring Batch 原生 `BatchObservabilityBeanPostProcessor` 替代自定义监听器，在 `BatchAutoConfiguration` 中注册，实现零配置集成。

## 后果

### 正面影响

- **零用户侵入**：无需修改任何 Job 定义代码
- **功能更完整**：原生支持 Job + Step 级别的 trace/span（自定义仅支持 Job）
- **减少维护**：删除 192 行自定义代码
- **官方支持**：使用 Spring Batch 推荐方案，随版本升级自动获得增强

### 负面影响

- **标签格式变化**：标签格式改为 Spring 标准格式（`spring.batch.job.*`），需要更新监控配置

### 风险

- 迁移期间需确认现有监控仪表盘兼容新标签格式

## 替代方案

### 方案 A：保持自定义监听器

继续使用自定义 `BatchObservationJobListener`，手动注入到每个 Job。

**优点**：
- 标签格式可完全自定义
- 无迁移成本

**缺点**：
- 用户需要手动注入，容易遗漏
- 仅支持 Job 级别，Step 级别需要额外开发
- 维护成本高，需跟进 Spring Batch 变化

### 方案 B：混合模式

同时支持原生和自定义两种方式，用户按需选择。

**优点**：
- 灵活性高，兼容历史配置

**缺点**：
- 复杂度增加，需要维护两套代码
- 可能导致指标重复

## 相关设计文档

本决策是整体可观测性方案的一部分：

- [[designs/observability/_MOC|可观测性系统设计]] - 完整的可观测性架构设计
- [[decisions/ADR-005-adopt-opentelemetry-grafana-stack-for-observability|ADR-005]] - 采用 OTel + Grafana Stack 的技术选型决策

## 参考资料

- [Spring Batch Observability](https://docs.spring.io/spring-batch/reference/monitoring-and-metrics.html)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
