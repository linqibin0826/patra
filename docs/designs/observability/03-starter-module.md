---
title: 可观测性系统设计 - Starter 模块设计
type: design
status: draft
date: 2025-11-29
module: patra-spring-boot-starter-observability
related_adrs: [ADR-005]
tags:
  - design/module
  - tech/opentelemetry
  - tech/micrometer
---

# Starter 模块设计

## 现有组件分析

### 目录结构

```
patra-spring-boot-starter-observability/
├── src/main/java/com/patra/starter/observability/
│   ├── autoconfigure/
│   │   ├── ObservabilityAutoConfiguration.java      ✅ 保留
│   │   ├── MicrometerAutoConfiguration.java         ✅ 保留（修改）
│   │   ├── SkyWalkingMeterAutoConfiguration.java    ❌ 删除
│   │   ├── PrometheusAutoConfiguration.java         ✅ 保留
│   │   └── ObservationInterceptorsAutoConfiguration.java  ✅ 保留
│   ├── config/
│   │   └── ObservabilityProperties.java             ✅ 保留（修改）
│   ├── handler/
│   │   ├── LoggingObservationHandler.java           ✅ 保留
│   │   └── PerformanceObservationHandler.java       ✅ 保留
│   ├── filter/
│   │   ├── SensitiveDataObservationFilter.java      ✅ 保留
│   │   ├── CommonTagsObservationFilter.java         ✅ 保留
│   │   ├── HighCardinalityMeterFilter.java          ✅ 保留
│   │   ├── MetricNamingMeterFilter.java             ✅ 保留
│   │   └── CommonTagsMeterFilter.java               ✅ 保留
│   └── interceptor/
│       ├── RestClientObservationInterceptor.java    ✅ 保留
│       ├── ObservationResolutionInterceptor.java    ✅ 保留
│       └── redisson/
│           └── LockMetricsRecorder.java             ✅ 保留
└── pom.xml                                          ✅ 修改
```

### 组件状态汇总

| 类别 | 保留 | 修改 | 删除 | 新增 |
|------|------|------|------|------|
| AutoConfiguration | 4 | 1 | 1 | 1 |
| Properties | 1 | 1 | 0 | 1 |
| Handler | 2 | 0 | 0 | 0 |
| Filter | 5 | 0 | 0 | 0 |
| Interceptor | 3 | 0 | 0 | 0 |

## 需要删除的组件

### SkyWalkingMeterAutoConfiguration

**文件路径：** `autoconfigure/SkyWalkingMeterAutoConfiguration.java`

**删除原因：**
- 依赖 `apm-toolkit-micrometer-registry`（SkyWalking 专有）
- 与 OpenTelemetry 标准不兼容
- 功能被 OTLP Exporter 替代

**影响分析：**
- 无其他组件依赖此类
- 移除后指标将通过 OTLP 导出

### SkyWalking 依赖

**需要从 `pom.xml` 移除：**

```xml
<!-- ❌ 删除 -->
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-micrometer-registry</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-trace</artifactId>
</dependency>
```

## 需要修改的组件

### ObservabilityProperties

**修改内容：**

1. **移除 SkyWalking 配置**

```java
// ❌ 删除
private SkyWalkingMeterConfig skywalking = new SkyWalkingMeterConfig();

public static class SkyWalkingMeterConfig {
    private boolean enabled = true;
    private String oapAddress = "skywalking-oap:11800";
}
```

2. **新增 OTLP Exporter 配置**

```java
// ✅ 新增
private OtlpExporterConfig exporter = new OtlpExporterConfig();

@Data
public static class OtlpExporterConfig {
    /// 是否启用 OTLP 导出。
    private boolean enabled = true;

    /// OTLP 端点地址。
    private String endpoint = "http://localhost:4317";

    /// 传输协议（grpc 或 http/protobuf）。
    private Protocol protocol = Protocol.GRPC;

    /// 导出超时时间。
    private Duration timeout = Duration.ofSeconds(10);

    /// 压缩方式。
    private Compression compression = Compression.GZIP;

    /// 自定义 Headers。
    private Map<String, String> headers = new HashMap<>();

    public enum Protocol {
        GRPC, HTTP_PROTOBUF
    }

    public enum Compression {
        NONE, GZIP
    }
}
```

3. **配置属性映射**

| 旧配置 | 新配置 | 说明 |
|--------|--------|------|
| `metrics.skywalking.enabled` | 删除 | 不再需要 |
| `metrics.skywalking.oap-address` | `exporter.endpoint` | 统一端点 |
| - | `exporter.protocol` | 新增协议选择 |
| - | `exporter.timeout` | 新增超时配置 |
| - | `exporter.compression` | 新增压缩配置 |

### MicrometerAutoConfiguration

**修改内容：**

新增 Micrometer → OpenTelemetry Bridge 依赖即可，Spring Boot 3.2+ 会自动配置：

```xml
<!-- pom.xml 添加依赖 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

> [!note] Spring Boot 自动配置
> Spring Boot 3.2+ 引入 `micrometer-tracing-bridge-otel` 依赖后，会自动配置：
> - `io.micrometer.tracing.Tracer`（桥接 OpenTelemetry Tracer）
> - `OtelCurrentTraceContext`（追踪上下文管理）
>
> 无需手动创建 Bean，符合"约定优于配置"原则。

## 需要新增的组件

### OtelAutoConfiguration

**文件路径：** `autoconfigure/OtelAutoConfiguration.java`

**职责：** 配置 OpenTelemetry SDK 和 OTLP Exporter

```java
package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.config.ObservabilityProperties.OtlpExporterConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/// OpenTelemetry SDK 自动配置。
///
/// 提供以下功能：
/// - 配置 OpenTelemetry Resource（服务标识）
/// - 配置 OTLP Span/Log Exporter
/// - 配置 TracerProvider 和 LoggerProvider
///
/// @author Jobs
/// @since 1.0.0
@AutoConfiguration(after = MicrometerAutoConfiguration.class)
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(
    prefix = "patra.observability.exporter",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OtelAutoConfiguration {

    /// 创建 OpenTelemetry Resource。
    ///
    /// Resource 包含服务元数据，用于标识遥测数据来源。
    @Bean
    @ConditionalOnMissingBean
    public Resource otelResource(
            @Value("${spring.application.name:unknown}") String applicationName,
            ObservabilityProperties properties) {
        String serviceName = properties.getApplicationName() != null
            ? properties.getApplicationName()
            : applicationName;

        return Resource.getDefault().merge(
            Resource.create(
                Attributes.builder()
                    .put(ServiceAttributes.SERVICE_NAME, serviceName)
                    .put(ServiceAttributes.SERVICE_VERSION, "0.1.0-SNAPSHOT")
                    .put("service.environment", properties.getEnvironment())
                    .put("service.cluster", properties.getCluster())
                    .build()));
    }

    /// 创建 OTLP Span Exporter。
    @Bean
    @ConditionalOnMissingBean
    public OtlpGrpcSpanExporter otlpGrpcSpanExporter(ObservabilityProperties properties) {
        OtlpExporterConfig config = properties.getExporter();
        var builder = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.getEndpoint())
            .setTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

        if (config.getCompression() == OtlpExporterConfig.Compression.GZIP) {
            builder.setCompression("gzip");
        }
        config.getHeaders().forEach(builder::addHeader);
        return builder.build();
    }

    /// 创建 OTLP Log Exporter（日志启用时）。
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "patra.observability.logging", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    public OtlpGrpcLogRecordExporter otlpGrpcLogRecordExporter(ObservabilityProperties properties) {
        OtlpExporterConfig config = properties.getExporter();
        var builder = OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(config.getEndpoint())
            .setTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

        if (config.getCompression() == OtlpExporterConfig.Compression.GZIP) {
            builder.setCompression("gzip");
        }
        config.getHeaders().forEach(builder::addHeader);
        return builder.build();
    }

    /// 创建 TracerProvider。
    @Bean
    @ConditionalOnMissingBean
    public SdkTracerProvider sdkTracerProvider(Resource resource, OtlpGrpcSpanExporter exporter) {
        return SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .build();
    }

    /// 创建 LoggerProvider（日志启用时）。
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "patra.observability.logging", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    public SdkLoggerProvider sdkLoggerProvider(Resource resource, OtlpGrpcLogRecordExporter logExporter) {
        return SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
            .build();
    }

    /// 创建 OpenTelemetry 实例。
    ///
    /// 整合 TracerProvider 和 LoggerProvider，提供统一的遥测入口。
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(
            SdkTracerProvider tracerProvider,
            ObjectProvider<SdkLoggerProvider> loggerProviderProvider) {
        SdkLoggerProvider loggerProvider = loggerProviderProvider.getIfAvailable();

        var builder = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider);

        if (loggerProvider != null) {
            builder.setLoggerProvider(loggerProvider);
        }
        return builder.build();
    }

    /// 创建 Tracer Bean（用于手动创建 Span）。
    @Bean
    @ConditionalOnMissingBean
    public Tracer otelTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("patra-observability", "0.1.0-SNAPSHOT");
    }
}
```

> [!important] 关键实现细节
> 1. **`ServiceAttributes` 替代 `ResourceAttributes`**：后者已被 OTel 标记为废弃
> 2. **显式加载顺序**：`@AutoConfiguration(after = MicrometerAutoConfiguration.class)` 确保 Micrometer 先初始化
> 3. **无全局注册**：使用 `.build()` 而非 `.buildAndRegisterGlobal()`，避免全局状态污染
> 4. **可选日志导出**：通过 `ObjectProvider<SdkLoggerProvider>` 优雅处理日志禁用场景

### OtlpLogbackAppender 配置

**文件路径：** `src/main/resources/logback-otel.xml`（供业务模块引用）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<included>
    <!-- OTLP Appender 配置 -->
    <appender name="OTLP" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
        <captureExperimentalAttributes>true</captureExperimentalAttributes>
        <captureCodeAttributes>true</captureCodeAttributes>
        <captureMdcAttributes>*</captureMdcAttributes>
    </appender>

    <!-- 异步包装，避免阻塞业务线程 -->
    <appender name="ASYNC_OTLP" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="OTLP"/>
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>true</neverBlock>
    </appender>
</included>
```

## 依赖变更

### pom.xml 修改

```xml
<dependencies>
    <!-- ========== 保留依赖 ========== -->

    <!-- Micrometer 观测 API -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-observation</artifactId>
    </dependency>

    <!-- Micrometer 指标核心 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- Prometheus Registry -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- ========== 删除依赖 ========== -->

    <!-- ❌ 删除 SkyWalking Meter Registry -->
    <!--
    <dependency>
        <groupId>org.apache.skywalking</groupId>
        <artifactId>apm-toolkit-micrometer-registry</artifactId>
    </dependency>
    -->

    <!-- ❌ 删除 SkyWalking Trace API -->
    <!--
    <dependency>
        <groupId>org.apache.skywalking</groupId>
        <artifactId>apm-toolkit-trace</artifactId>
    </dependency>
    -->

    <!-- ========== 新增依赖 ========== -->

    <!-- ✅ OpenTelemetry API -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
    </dependency>

    <!-- ✅ OpenTelemetry SDK -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
    </dependency>

    <!-- ✅ OTLP Exporter (gRPC) -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <!-- ✅ Micrometer Tracing Bridge -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- ✅ Logback OTLP Appender -->
    <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    </dependency>

    <!-- ✅ OpenTelemetry Semantic Conventions -->
    <dependency>
        <groupId>io.opentelemetry.semconv</groupId>
        <artifactId>opentelemetry-semconv</artifactId>
    </dependency>
</dependencies>
```

### 版本管理（patra-parent/pom.xml）

```xml
<properties>
    <!-- OpenTelemetry 版本 -->
    <opentelemetry.version>1.35.0</opentelemetry.version>
    <opentelemetry-instrumentation.version>2.1.0</opentelemetry-instrumentation.version>
    <opentelemetry-semconv.version>1.23.1-alpha</opentelemetry-semconv.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- OpenTelemetry BOM -->
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${opentelemetry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- OpenTelemetry Instrumentation BOM -->
        <dependency>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-instrumentation-bom</artifactId>
            <version>${opentelemetry-instrumentation.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 自动配置加载顺序

### META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

```
# 1. 核心配置（最先加载）
com.patra.starter.observability.autoconfigure.ObservabilityAutoConfiguration

# 2. Micrometer 配置
com.patra.starter.observability.autoconfigure.MicrometerAutoConfiguration

# 3. OpenTelemetry 配置（新增）
com.patra.starter.observability.autoconfigure.OtelAutoConfiguration

# 4. Prometheus 配置
com.patra.starter.observability.autoconfigure.PrometheusAutoConfiguration

# 5. 拦截器配置（最后加载）
com.patra.starter.observability.autoconfigure.ObservationInterceptorsAutoConfiguration
```

### 加载依赖图

```mermaid
%%{init: {
  'theme': 'base',
  'themeVariables': {
    'primaryColor': '#dbeafe',
    'primaryTextColor': '#1e293b',
    'primaryBorderColor': '#3b82f6',
    'lineColor': '#64748b',
    'edgeLabelBackground': '#f1f5f9'
  }
}}%%
flowchart TD
    subgraph AutoConfiguration["自动配置加载顺序"]
        direction TB

        OBS[ObservabilityAutoConfiguration]
        MIC[MicrometerAutoConfiguration]
        OTEL[OtelAutoConfiguration]
        PROM[PrometheusAutoConfiguration]
        INTER[ObservationInterceptorsAutoConfiguration]

        OBS -->|@AutoConfigureBefore| MIC
        MIC -->|@AutoConfigureBefore| OTEL
        OTEL -->|@AutoConfigureBefore| PROM
        PROM -->|@AutoConfigureBefore| INTER
    end

    subgraph Beans["注册的 Beans"]
        direction TB

        OBS_B[ObservationRegistry]
        MIC_B[MeterFilter\nObservationFilter\nObservationHandler]
        OTEL_B[OpenTelemetry\nTracer\nSpanExporter]
        PROM_B[PrometheusMeterRegistry]
        INTER_B[RestClientObservationInterceptor\nObservationResolutionInterceptor]
    end

    OBS --> OBS_B
    MIC --> MIC_B
    OTEL --> OTEL_B
    PROM --> PROM_B
    INTER --> INTER_B

    classDef default fill:#dbeafe,stroke:#3b82f6,color:#1e293b;
    classDef bean fill:#ede9fe,stroke:#8b5cf6,color:#1e293b;
    class OBS_B,MIC_B,OTEL_B,PROM_B,INTER_B bean;
```

## 配置属性完整结构

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
      prefix: ""
      step: 60s
      common-tags:
        team: patra
      prometheus:
        enabled: true
        enable-exemplars: true

    # OTLP 导出配置（新增）
    exporter:
      enabled: true
      endpoint: http://localhost:4317
      protocol: grpc
      timeout: 10s
      compression: gzip
      headers: {}

    # 追踪配置
    tracing:
      enabled: true
      sampling-rate: 1.0
      baggage-fields:
        - X-Request-Id
        - X-Correlation-Id

    # 日志配置
    logging:
      enabled: true
      include-trace-id: true
      pattern: "[%tid] [${spring.application.name},%X{traceId:-},%X{spanId:-}]"

    # Handler 配置
    handlers:
      logging:
        enabled: true
        log-level: DEBUG
      performance:
        enabled: true
        slow-threshold: 3s

    # 安全配置
    security:
      mask-sensitive-data: true
      sensitive-patterns: []
      masking-disabled-in-environments:
        - dev-local
```

## 相关链接

- 上一章：[[02-architecture|架构设计]]
- 下一章：[[04-otel-integration|OTel 集成方案]]
- 索引：[[_MOC|可观测性系统设计]]
