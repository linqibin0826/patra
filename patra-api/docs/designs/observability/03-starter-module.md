---
title: 可观测性系统设计 - Starter 模块设计
type: design
status: completed
date: 2025-11-29
module: patra-spring-boot-starter-observability
related_adrs: [ADR-005]
tags:
  - design/module
  - tech/opentelemetry
  - tech/micrometer
---

# Starter 模块设计

## 架构说明

> [!important] 核心架构决策
> 本模块采用 **OTel Java Agent + Micrometer Bridge** 模式：
> - **Tracing**: OTel Java Agent 通过 `-javaagent` 参数自动处理（零代码侵入）
> - **Metrics**: OTel Agent + Micrometer Bridge，统一走 OTLP 导出到 OTel Collector
> - **Logging**: Agent 自动注入 `trace_id`/`span_id` 到 MDC

## 目录结构

```
patra-spring-boot-starter-observability/
├── src/main/java/com/patra/starter/observability/
│   ├── autoconfigure/
│   │   ├── ObservabilityAutoConfiguration.java           # 核心配置
│   │   ├── MicrometerAutoConfiguration.java              # Micrometer + OTel Bridge
│   │   └── ObservationInterceptorsAutoConfiguration.java # 拦截器配置
│   ├── config/
│   │   └── ObservabilityProperties.java                  # 配置属性
│   ├── filter/
│   │   ├── HighCardinalityMeterFilter.java               # 高基数过滤
│   │   └── CommonTagsMeterFilter.java                    # 通用标签
│   └── interceptor/
│       └── ObservationResolutionInterceptor.java         # 观测拦截器
├── src/main/resources/
│   └── META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── pom.xml
```

## 组件状态汇总

| 类别 | 组件数 | 说明 |
|------|--------|------|
| AutoConfiguration | 3 | 核心、Micrometer（含 OTel Bridge）、拦截器 |
| Properties | 1 | ObservabilityProperties |
| MeterFilter | 2 | 高基数过滤、公共标签（命名前缀由 OTel Collector 统一添加） |
| Interceptor | 1 | ObservationResolutionInterceptor |

> **注意**：Logback MDC 转换器（TraceId、SpanId）位于 `patra-spring-boot-starter-core` 模块。

> [!note] 已移除组件
> - **ObservationHandler**：Tracing 由 OTel Java Agent 自动处理
> - **ObservationFilter**：公共标签由 MeterFilter 统一处理

## 自动配置类

### ObservabilityAutoConfiguration

**职责**：可观测性核心配置和属性绑定

```java
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnClass({ObservationRegistry.class, MeterRegistry.class})
@ConditionalOnProperty(prefix = "patra.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityAutoConfiguration {
    // 创建 ObservationRegistry Bean
    // 创建内部 ObservedAspectConfiguration 处理 @Observed 注解
}
```

### MicrometerAutoConfiguration

**职责**：Micrometer MeterFilter + OTel Bridge 配置

注册的 Bean：
- `HighCardinalityMeterFilter` - 高基数过滤（防止时序爆炸）
- `CommonTagsMeterFilter` - 添加公共标签（application、environment、region、cluster）
- `otelMeterRegistryBridge` - 将 OTel Agent 的 MeterRegistry 暴露为 Primary Bean

> **指标前缀**：`patra_` 前缀由 OTel Collector 的 Prometheus exporter `namespace: patra` 配置统一添加。

### ObservationInterceptorsAutoConfiguration

**职责**：可观测性拦截器自动配置

```java
@AutoConfiguration(after = MicrometerAutoConfiguration.class)
public class ObservationInterceptorsAutoConfiguration {
    // 创建 ObservationResolutionInterceptor - 错误解析流程可观测性拦截器
}
```

## 自动配置加载顺序

```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

# 1. 主配置：可观测性核心配置和属性绑定
com.patra.starter.observability.autoconfigure.ObservabilityAutoConfiguration

# 2. Micrometer 配置：MeterFilter + OTel Bridge
com.patra.starter.observability.autoconfigure.MicrometerAutoConfiguration

# 3. 拦截器配置：插件式架构
com.patra.starter.observability.autoconfigure.ObservationInterceptorsAutoConfiguration
```

## 依赖配置

### pom.xml

```xml
<dependencies>
    <!-- Patra 内部依赖 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-common-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Micrometer (Metrics + Observations) -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-observation</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- Caffeine Cache: 状态管理 -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>

    <!-- Jakarta Validation API -->
    <dependency>
        <groupId>jakarta.validation</groupId>
        <artifactId>jakarta.validation-api</artifactId>
    </dependency>

    <!-- Jakarta Servlet API (可选) -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

> [!note] 无 OpenTelemetry SDK 依赖
> Tracing 由 OTel Java Agent 通过 `-javaagent` 参数自动处理，无需在应用代码中引入 OTel SDK 依赖。
> 这实现了**零代码侵入**的可观测性。

## 配置属性

### ObservabilityProperties

```java
@ConfigurationProperties(prefix = "patra.observability")
public class ObservabilityProperties {
    // 全局配置
    private boolean enabled = true;
    private String applicationName;
    private String environment;  // 自动从 spring.profiles.active 获取，"default" 映射为 "dev"
    private String region;
    private String cluster = "default";

    // 主要配置块
    private MetricsConfig metrics = new MetricsConfig();
}
```

### 配置属性完整结构

```yaml
patra:
  observability:
    # 全局开关
    enabled: true

    # 应用标识
    application-name: ${spring.application.name}
    environment: dev
    region: cn-east-1
    cluster: default

    # 指标配置
    metrics:
      enabled: true
      common-tags:
        team: patra
```

> **指标前缀**：`patra_` 前缀由 OTel Collector 的 Prometheus exporter `namespace: patra` 配置统一添加，无需应用层配置。

> [!tip] Tracing 配置
> Tracing 相关配置（如采样率）通过 OTel Agent JVM 参数控制：
> ```bash
> -Dotel.traces.sampler=parentbased_traceidratio
> -Dotel.traces.sampler.arg=1.0
> ```

## MeterFilter 详解

| Filter | 职责 | 执行顺序 |
|--------|------|----------|
| `HighCardinalityMeterFilter` | 过滤高基数标签（userId、traceId 等），防止 Prometheus OOM | HIGHEST_PRECEDENCE |
| `CommonTagsMeterFilter` | 为所有 Meter 添加公共标签（application、environment、region、cluster） | LOWEST_PRECEDENCE |

> **指标前缀**：`patra_` 前缀由 OTel Collector 的 Prometheus exporter `namespace: patra` 配置统一添加，无需应用层 Filter。

> [!important] 架构简化
> ObservationHandler 和 ObservationFilter 已移除：
> - Tracing 由 OTel Java Agent 自动处理
> - 公共标签由 MeterFilter 统一添加（作用于 Metrics）

## 相关链接

- 上一章：[[02-architecture|架构设计]]
- 下一章：[[04-otel-integration|OTel 集成方案]]
- 索引：[[_MOC|可观测性系统设计]]
