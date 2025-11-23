# Patra 统一可观测性 Starter 设计方案

> **版本**: 1.0.0
> **状态**: 设计阶段
> **作者**: Jobs (Claude AI Assistant)
> **日期**: 2025-11-23
> **项目类型**: 绿地项目（Greenfield Project）- 可进行破坏性重构

---

## 📋 文档目录

- [执行摘要](#执行摘要)
- [背景与目标](#背景与目标)
- [现状分析](#现状分析)
- [架构设计](#架构设计)
- [技术选型](#技术选型)
- [模块设计](#模块设计)
- [API 设计](#api-设计)
- [配置设计](#配置设计)
- [实施计划](#实施计划)
- [重构策略](#重构策略)
- [测试策略](#测试策略)
- [性能评估](#性能评估)
- [风险评估](#风险评估)
- [成功标准](#成功标准)
- [附录](#附录)

---

## 执行摘要

### 核心目标
设计并实现一个**统一的、生产级的、一步到位的**可观测性 Starter，整合 **Metrics（指标）**、**Tracing（追踪）**、**Logging（日志）** 三大支柱，为 Patra 项目提供完整的可观测性解决方案。

### 关键决策
1. **破坏性重构**：基于绿地项目特性，直接实现最优架构，不考虑向后兼容
2. **统一抽象**：基于 Spring Boot 3.x 的 Micrometer Observation API 构建统一抽象层
3. **混合后端**：同时支持 SkyWalking（主）+ Prometheus（辅）双后端
4. **零代码侵入**：通过 AutoConfiguration 和 AOP 实现自动化集成
5. **集中管理**：所有可观测性配置集中在一个 Starter 中

### 预期收益
- ✅ **统一标准**：所有服务使用一致的可观测性配置和 API
- ✅ **开箱即用**：添加依赖即可自动启用分布式追踪和指标收集
- ✅ **生产就绪**：基于业界最佳实践，性能开销可控（< 10%）
- ✅ **易于扩展**：通过 SPI 和 ObservationHandler 机制支持自定义扩展
- ✅ **完整文档**：提供详尽的使用文档、配置示例和故障排查指南

---

## 背景与目标

### 项目背景

**Patra** 是一个医学文献数据平台，采用微服务架构 + 六边形架构 + DDD，目前处于绿地项目阶段（无历史包袱，可自由重构）。

**现状问题**：
1. **SkyWalking 未启用**：依赖已引入，但 Java Agent 未配置，分布式追踪无法工作
2. **指标分散**：8 个模块各自实现指标收集，缺乏统一标准和管理
3. **配置混乱**：追踪、指标、日志配置分散在各个 Starter 中
4. **Batch 缺失**：Spring Batch 的可观测性仅有 TODO 标记，未实现
5. **命名不统一**：指标命名、标签使用、配置前缀缺乏一致性

### 设计目标

#### 主要目标
1. **统一可观测性平台**
   - 集成 Metrics、Tracing、Logging 三大支柱
   - 统一配置入口和管理方式
   - 统一命名规范和标签体系

2. **生产级可靠性**
   - 性能开销可控（CPU < 10%，内存 < 50MB）
   - 支持优雅降级和熔断保护
   - 完善的错误处理和日志记录

3. **开发者友好**
   - 零代码侵入，自动配置
   - 合理的默认值，开箱即用
   - 详尽的文档和示例

4. **高度可扩展**
   - 支持自定义 ObservationHandler
   - 支持多后端导出（SkyWalking、Prometheus、Grafana）
   - 支持业务自定义指标和追踪

#### 次要目标
1. **技术债务清理**：重构现有分散的可观测性实现
2. **标准化**：建立可观测性最佳实践和编码规范
3. **工具链完善**：提供 Grafana Dashboard、告警规则模板

### 非目标
1. **APM 替代品**：不是完整的 APM 解决方案，依赖 SkyWalking 作为后端
2. **业务监控**：不包含业务指标的定义，由各服务自行实现
3. **日志聚合**：不提供日志聚合功能，依赖 ELK Stack

---

## 现状分析

### 现有可观测性实现分布

根据调研，现有可观测性实现分散在以下模块：

| 模块 | 功能 | 实现位置 | 问题 |
|-----|------|---------|------|
| **patra-starter-core** | 追踪传播、MDC、错误观测 | `TracingInterceptor`, `ErrorObservationRecorder` | 配置分散，缺乏统一管理 |
| **patra-starter-rest-client** | HTTP 请求指标 | `MetricsInterceptor`, `TracingInterceptor` | 独立实现，命名不统一 |
| **patra-starter-object-storage** | 存储操作指标 | `ObjectStorageMetrics` | 独立实现 |
| **patra-starter-provenance** | API 调用指标 | `ProvenanceMetrics` | 独立实现 |
| **patra-starter-expr** | 编译器指标 | `ExprMetrics` | 独立实现 |
| **patra-starter-batch** | Batch Job 可观测性 | `ObservabilityAutoConfiguration` (TODO) | 未实现 |
| **patra-catalog-infra** | MeSH 导入指标 | `MeshImportMetrics` | 业务层实现 |
| **patra-ingest-app** | Outbox 指标 | `OutboxMetrics`, `OutboxRelayMetrics` | 业务层实现 |
| **patra-starter-feign** | Feign 错误观测 | `MicrometerFeignErrorObservationRecorder` | 独立实现 |
| **patra-starter-redisson** | 分布式锁指标 | `LockMetricsRecorder` | 独立实现 |

### 关键发现

#### 优势
- ✅ **基础完善**：Micrometer 依赖已引入，MeterRegistry 可用
- ✅ **指标丰富**：关键业务场景已有指标收集
- ✅ **SkyWalking 准备**：依赖和日志集成已完成
- ✅ **条件装配**：大量使用 `@ConditionalOnBean` 等注解

#### 问题
- ❌ **缺乏统一标准**：指标命名、标签使用各异
  - REST Client: `rest_client_requests_success_total`
  - Object Storage: `patra.object_storage.upload.total`
  - Provenance: `provenance.client.api.duration`
- ❌ **配置分散**：追踪、指标配置散落在各 Starter 的 Properties 中
- ❌ **SkyWalking Agent 未启动**：无法进行分布式追踪
- ❌ **重复实现**：MetricsInterceptor、TracingInterceptor 在多个模块重复
- ❌ **缺乏全局控制**：无法统一开关、调整采样率、配置公共标签

### 业界对标

参考以下业界方案：
- **Spring Cloud Sleuth** (已废弃，迁移到 Micrometer Tracing)
- **Spring Boot Actuator** + Micrometer Observation
- **OpenTelemetry Java Agent**
- **SkyWalking Toolkit**

**最佳实践选择**：
> **Micrometer Observation API** 作为统一抽象层，SkyWalking 作为主要后端，Prometheus 作为辅助后端。

---

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Patra 微服务应用                                 │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              业务代码 (Domain, App, Infra, Adapter)             │   │
│  │  - 可选：使用 @Observed 注解标记关键方法                        │   │
│  │  - 可选：注入 MeterRegistry 自定义指标                          │   │
│  └────────────────────────┬────────────────────────────────────────┘   │
│                           │                                              │
│  ┌────────────────────────▼────────────────────────────────────────┐   │
│  │          patra-spring-boot-starter-observability                │   │
│  │  ┌────────────────────────────────────────────────────────┐    │   │
│  │  │         Micrometer Observation API (统一抽象)          │    │   │
│  │  │  - ObservationRegistry (自动配置)                      │    │   │
│  │  │  - @Observed AOP 支持                                  │    │   │
│  │  │  - ObservationHandler 链                               │    │   │
│  │  └────────┬─────────────────────────────┬─────────────────┘    │   │
│  │           │                             │                       │   │
│  │  ┌────────▼──────────┐        ┌────────▼──────────────┐       │   │
│  │  │  Metrics Path     │        │   Tracing Path        │       │   │
│  │  │  (Micrometer)     │        │   (SkyWalking Agent)  │       │   │
│  │  └─────────┬─────────┘        └───────────┬───────────┘       │   │
│  │            │                               │                    │   │
│  │  ┌─────────▼──────────┐         ┌─────────▼──────────┐        │   │
│  │  │ CompositeMeter     │         │  SkyWalking Java   │        │   │
│  │  │ Registry           │         │  Agent (Bytecode   │        │   │
│  │  │  ├─ Skywalking     │         │  Instrumentation)  │        │   │
│  │  │  └─ Prometheus     │         │  - Trace Context   │        │   │
│  │  └─────────┬──────────┘         │  - Span 生成       │        │   │
│  │            │                     └──────────┬─────────┘        │   │
│  └────────────┼────────────────────────────────┼──────────────────┘   │
│               │                                │                       │
│  ┌────────────▼────────────────────────────────▼──────────────────┐   │
│  │              现有 Starter (重构后集成)                           │   │
│  │  - rest-client: 使用统一 Observation API                        │   │
│  │  - object-storage: 使用统一 Metrics API                         │   │
│  │  - batch: 实现 ObservationJobListener                           │   │
│  │  - feign: 使用统一错误观测                                       │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────────┘
             │                                 │
             ▼                                 ▼
    ┌────────────────┐              ┌──────────────────┐
    │  SkyWalking    │              │  SkyWalking      │
    │  OAP Server    │◄─────────────│  OAP Server      │
    │  (Meter        │  gRPC 11800  │  (Trace          │
    │   Receiver)    │              │   Receiver)      │
    └────────┬───────┘              └──────────┬───────┘
             │                                 │
             ▼                                 ▼
    ┌────────────────┐              ┌──────────────────┐
    │  Elasticsearch │              │  Elasticsearch   │
    │  (存储)        │              │  (存储)          │
    └────────────────┘              └──────────────────┘
             │                                 │
             └─────────────┬───────────────────┘
                           ▼
                  ┌──────────────────┐
                  │  SkyWalking UI   │
                  │  (可视化)        │
                  │  localhost:8088  │
                  └──────────────────┘
```

### 三层架构

#### 第一层：统一抽象层（patra-spring-boot-starter-observability）
**职责**：
- 提供统一的可观测性 API 和配置
- 管理 ObservationRegistry 和 MeterRegistry
- 注册和管理 ObservationHandler
- 配置公共标签和命名规范
- 提供自动配置和条件装配

**核心组件**：
- `ObservabilityAutoConfiguration`：主配置类
- `ObservabilityProperties`：统一配置属性
- `ObservationHandlerChain`：Handler 管理
- `CommonTagsCustomizer`：公共标签配置
- `MetricNamingConvention`：指标命名规范

#### 第二层：后端集成层
**职责**：
- 集成 SkyWalking Agent（分布式追踪）
- 集成 SkyWalking Meter Registry（指标收集）
- 集成 Prometheus Registry（指标导出）
- 配置 Logback + SkyWalking（日志关联）

**核心组件**：
- `SkyWalkingMeterAutoConfiguration`：SkyWalking Meter 配置
- `PrometheusAutoConfiguration`：Prometheus 配置
- `LogbackAutoConfiguration`：日志配置
- `TracePropagationFilter`：追踪上下文传播

#### 第三层：应用层（现有 Starter 重构）
**职责**：
- 使用统一的 Observation API 进行追踪
- 使用统一的 MeterRegistry 进行指标收集
- 遵守统一的命名规范和标签体系

**重构内容**：
- REST Client：使用 Observation.createNotStarted()
- Object Storage：统一指标命名为 `patra.storage.*`
- Batch：实现 ObservationJobListener
- Feign：使用统一错误观测
- Provenance：统一指标命名为 `patra.provenance.*`

### 数据流

#### Metrics 数据流
```
业务代码
  ↓ (注入 MeterRegistry)
Counter.increment() / Timer.record()
  ↓
CompositeMeterRegistry
  ├─→ SkywalkingMeterRegistry → SkyWalking OAP (gRPC:11800)
  └─→ PrometheusMeterRegistry → /actuator/prometheus (HTTP:8080)
```

#### Tracing 数据流
```
HTTP 请求到达
  ↓
SkyWalking Agent 自动拦截
  ↓
创建 Trace 和 Span
  ↓
TraceContext.traceId() 注入到 MDC
  ↓
业务代码执行（自动或手动 @Trace）
  ↓
SkyWalking Agent 上报 → OAP Server (gRPC:11800)
```

#### Logging 数据流
```
业务代码调用 log.info()
  ↓
Logback 拦截
  ↓
TraceIdConverter 从 SkyWalking TraceContext 提取 traceId
  ↓
MDC 注入 traceId, spanId, segmentId
  ↓
日志输出：[trace:abc123,seg:001,span:789] Log message
  ↓ (可选)
Logstash 收集 → Elasticsearch → Kibana 可视化
```

---

## 技术选型

### 核心依赖

| 组件 | 版本 | 用途 | 必需/可选 |
|-----|------|------|----------|
| **Spring Boot** | 3.5.7 | 基础框架 | 必需 |
| **Spring Boot Actuator** | 3.5.7 | 端点暴露 | 必需 |
| **Micrometer Core** | 1.14.0+ | 指标抽象 | 必需 |
| **Micrometer Observation** | 1.14.0+ | 可观测性抽象 | 必需 |
| **SkyWalking Java Agent** | 9.5.0 | 分布式追踪 | 必需 |
| **SkyWalking Toolkit Trace** | 9.5.0 | 手动埋点 | 必需 |
| **SkyWalking Toolkit Logback** | 9.5.0 | 日志集成 | 必需 |
| **SkyWalking Micrometer Registry** | 9.5.0 | 指标导出 | 必需 |
| **Micrometer Registry Prometheus** | 1.14.0+ | Prometheus 导出 | 可选 |
| **Logback** | 1.5.x | 日志框架 | 必需 |
| **Spring AOP** | 3.5.7 | @Observed 支持 | 必需 |

### 依赖关系图

```
patra-spring-boot-starter-observability
├── spring-boot-starter-actuator (必需)
├── spring-boot-starter-aop (必需，支持 @Observed)
├── micrometer-core (必需，Actuator 自动引入)
├── micrometer-observation (必需，Actuator 自动引入)
├── apm-toolkit-trace (必需)
├── apm-toolkit-logback-1.x (必需)
├── apm-toolkit-micrometer-registry (必需)
└── micrometer-registry-prometheus (可选)

patra-spring-boot-starter-core
└── patra-spring-boot-starter-observability (新增依赖)

其他 patra-starter-*
└── patra-spring-boot-starter-core (已有依赖，间接依赖 observability)
```

### 技术选型理由

#### 为什么选择 Micrometer Observation？
1. **Spring Boot 3.x 官方推荐**：替代已废弃的 Spring Cloud Sleuth
2. **统一抽象**：一次埋点，同时支持 Metrics 和 Tracing
3. **与 Spring 生态集成**：与 Spring MVC、WebFlux、Batch 无缝集成
4. **灵活扩展**：通过 ObservationHandler 机制支持自定义逻辑

#### 为什么选择 SkyWalking？
1. **APM 完整性**：同时支持 Metrics、Tracing、Logging
2. **性能优秀**：基准测试显示 CPU 开销仅 10%
3. **中文友好**：国内社区活跃，文档齐全
4. **字节码增强**：无代码侵入，自动拦截 HTTP、DB、RPC
5. **已有基础**：项目已引入 SkyWalking 依赖和 Docker 环境

#### 为什么保留 Prometheus？
1. **生态丰富**：Grafana 社区 Dashboard 丰富
2. **灵活查询**：PromQL 强大，适合临时分析
3. **Pull 模式**：适合服务发现场景
4. **备份方案**：SkyWalking 故障时仍可通过 Prometheus 查看指标

---

## 模块设计

### 目录结构

```
patra-spring-boot-starter-observability/
├── pom.xml
├── README.md
├── src/main/java/com/patra/starter/observability/
│   ├── autoconfigure/                           # 自动配置
│   │   ├── ObservabilityAutoConfiguration.java              # 主配置类
│   │   ├── MicrometerAutoConfiguration.java                 # Micrometer 配置
│   │   ├── SkyWalkingMeterAutoConfiguration.java            # SkyWalking Meter
│   │   ├── PrometheusAutoConfiguration.java                 # Prometheus 配置
│   │   ├── LogbackAutoConfiguration.java                    # Logback 配置
│   │   ├── ObservationHandlerAutoConfiguration.java         # Handler 配置
│   │   └── package-info.java
│   ├── config/                                  # 配置属性
│   │   ├── ObservabilityProperties.java                     # 主配置属性
│   │   ├── MetricsProperties.java                           # 指标配置
│   │   ├── TracingProperties.java                           # 追踪配置
│   │   ├── LoggingProperties.java                           # 日志配置
│   │   └── package-info.java
│   ├── handler/                                 # ObservationHandler
│   │   ├── LoggingObservationHandler.java                   # 日志 Handler
│   │   ├── PerformanceObservationHandler.java               # 性能 Handler
│   │   ├── SensitiveDataMaskingHandler.java                 # 敏感数据脱敏 Handler (P0)
│   │   ├── AlertingObservationHandler.java                  # 告警 Handler (未来)
│   │   └── package-info.java
│   ├── filter/                                  # MeterFilter
│   │   ├── CommonTagsMeterFilter.java                       # 公共标签
│   │   ├── MetricNamingMeterFilter.java                     # 命名规范
│   │   ├── HighCardinalityMeterFilter.java                  # 高基数过滤
│   │   └── package-info.java
│   ├── convention/                              # 命名规范
│   │   ├── PatraNamingConvention.java                       # Patra 命名规范
│   │   └── package-info.java
│   ├── context/                                 # 上下文传播
│   │   ├── TraceContextHolder.java                          # 追踪上下文
│   │   ├── BaggageManager.java                              # Baggage 管理
│   │   └── package-info.java
│   ├── spi/                                     # 扩展点
│   │   ├── ObservabilityCustomizer.java                     # 自定义扩展接口
│   │   └── package-info.java
│   └── package-info.java                        # 包级文档
├── src/main/resources/
│   ├── META-INF/
│   │   ├── spring/
│   │   │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   └── additional-spring-configuration-metadata.json
│   ├── logback-observability.xml                # Logback 配置模板
│   └── application-observability.yml            # 默认配置模板
└── src/test/java/com/patra/starter/observability/
    ├── autoconfigure/
    │   ├── ObservabilityAutoConfigurationTest.java
    │   └── MicrometerAutoConfigurationTest.java
    ├── handler/
    │   └── PerformanceObservationHandlerTest.java
    └── TestObservabilityApplication.java        # 测试应用
```

### 核心类设计

#### 1. ObservabilityProperties

```java
package com.patra.starter.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;
import java.time.Duration;
import java.util.*;

/**
 * Patra 可观测性统一配置属性
 *
 * @author Jobs
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "patra.observability")
@Data
public class ObservabilityProperties {

    /**
     * 全局开关
     */
    private boolean enabled = true;

    /**
     * 应用标识
     */
    private String applicationName;

    /**
     * 环境标识 (dev, staging, prod)
     */
    private String environment = "dev";

    /**
     * 区域标识 (cn-east-1, us-west-2)
     */
    private String region;

    /**
     * 集群标识
     */
    private String cluster = "default";

    /**
     * 指标配置
     */
    private MetricsConfig metrics = new MetricsConfig();

    /**
     * 追踪配置
     */
    private TracingConfig tracing = new TracingConfig();

    /**
     * 日志配置
     */
    private LoggingConfig logging = new LoggingConfig();

    /**
     * ObservationHandler 配置
     */
    private HandlersConfig handlers = new HandlersConfig();

    /**
     * 指标配置
     */
    @Data
    public static class MetricsConfig {
        /**
         * 是否启用指标收集
         */
        private boolean enabled = true;

        /**
         * 指标前缀（可选，默认为空）
         */
        private String prefix = "";

        /**
         * 公共标签（自动添加到所有指标）
         */
        private Map<String, String> commonTags = new HashMap<>();

        /**
         * 导出间隔
         */
        private Duration step = Duration.ofSeconds(60);

        /**
         * SkyWalking Meter Registry 配置
         */
        private SkyWalkingMeterConfig skywalking = new SkyWalkingMeterConfig();

        /**
         * Prometheus Registry 配置
         */
        private PrometheusConfig prometheus = new PrometheusConfig();
    }

    /**
     * SkyWalking Meter 配置
     */
    @Data
    public static class SkyWalkingMeterConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * OAP 服务器地址
         */
        private String oapAddress = "skywalking-oap:11800";
    }

    /**
     * Prometheus 配置
     */
    @Data
    public static class PrometheusConfig {
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 是否启用 Exemplars（与 Tracing 关联）
         */
        private boolean enableExemplars = true;
    }

    /**
     * 追踪配置
     */
    @Data
    public static class TracingConfig {
        /**
         * 是否启用追踪
         */
        private boolean enabled = true;

        /**
         * 采样率 (0.0 - 1.0)
         */
        private double samplingRate = 1.0;

        /**
         * Baggage 传播字段
         */
        private List<String> baggageFields = Arrays.asList(
            "X-Request-Id",
            "X-Correlation-Id"
        );

        /**
         * 追踪 Header 名称
         */
        private List<String> headerNames = Arrays.asList(
            "X-Trace-ID",
            "X-B3-TraceId",
            "traceparent"
        );
    }

    /**
     * 日志配置
     */
    @Data
    public static class LoggingConfig {
        /**
         * 是否启用日志关联
         */
        private boolean enabled = true;

        /**
         * 是否在日志中包含 traceId
         */
        private boolean includeTraceId = true;

        /**
         * 日志格式模式
         */
        private String pattern = "[%tid] [${spring.application.name},%X{traceId:-},%X{spanId:-}]";
    }

    /**
     * ObservationHandler 配置
     */
    @Data
    public static class HandlersConfig {
        /**
         * 日志 Handler
         */
        private LoggingHandlerConfig logging = new LoggingHandlerConfig();

        /**
         * 性能 Handler
         */
        private PerformanceHandlerConfig performance = new PerformanceHandlerConfig();

        /**
         * 告警 Handler（未来扩展）
         */
        private AlertingHandlerConfig alerting = new AlertingHandlerConfig();
    }

    /**
     * 日志 Handler 配置
     */
    @Data
    public static class LoggingHandlerConfig {
        private boolean enabled = true;
        private String logLevel = "DEBUG";
    }

    /**
     * 性能 Handler 配置
     */
    @Data
    public static class PerformanceHandlerConfig {
        private boolean enabled = true;
        private Duration slowThreshold = Duration.ofSeconds(3);
    }

    /**
     * 告警 Handler 配置
     */
    @Data
    public static class AlertingHandlerConfig {
        private boolean enabled = false;
    }
}
```

#### 2. ObservabilityAutoConfiguration

```java
package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Patra 可观测性自动配置
 *
 * <p>提供统一的可观测性基础设施，包括：
 * <ul>
 *   <li>ObservationRegistry 配置和 Handler 注册</li>
 *   <li>公共标签配置</li>
 *   <li>@Observed 注解支持</li>
 *   <li>命名规范配置</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ObservationRegistry.class, MeterRegistry.class})
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "patra.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    public ObservabilityAutoConfiguration() {
        log.info("初始化 Patra 可观测性自动配置");
    }

    /**
     * 创建或注入 ObservationRegistry
     *
     * <p>ObservationRegistry 是 Micrometer Observation API 的核心，
     * 用于创建和管理 Observation 实例。
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        log.info("创建 ObservationRegistry");
        return ObservationRegistry.create();
    }

    /**
     * 启用 @Observed 注解支持
     *
     * <p>通过 AOP 拦截标注 @Observed 的方法，自动创建 Observation。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "management.observations.annotations",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("启用 @Observed 注解支持");
        return new ObservedAspect(observationRegistry);
    }
}
```

#### 3. PerformanceObservationHandler

```java
package com.patra.starter.observability.handler;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能观测处理器
 *
 * <p>功能：
 * <ul>
 *   <li>记录 Observation 的执行时间</li>
 *   <li>检测慢操作并记录警告日志</li>
 *   <li>收集性能统计信息</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
public class PerformanceObservationHandler implements ObservationHandler<Observation.Context> {

    private static final Logger log = LoggerFactory.getLogger(PerformanceObservationHandler.class);

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final Duration slowThreshold;

    public PerformanceObservationHandler(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
        log.info("初始化性能观测处理器，慢操作阈值: {}ms", slowThreshold.toMillis());
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStart(Observation.Context context) {
        String key = getKey(context);
        startTimes.put(key, System.currentTimeMillis());
        log.trace("观测开始: {}", context.getName());
    }

    @Override
    public void onStop(Observation.Context context) {
        String key = getKey(context);
        Long startTime = startTimes.remove(key);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > slowThreshold.toMillis()) {
                log.warn("慢操作检测: {} 耗时 {}ms，超过阈值 {}ms",
                    context.getName(), duration, slowThreshold.toMillis());
            } else {
                log.debug("观测完成: {} 耗时 {}ms", context.getName(), duration);
            }
        }
    }

    @Override
    public void onError(Observation.Context context) {
        log.error("观测错误: {}, 错误: {}",
            context.getName(),
            context.getError() != null ? context.getError().getMessage() : "Unknown");
    }

    private String getKey(Observation.Context context) {
        return context.getName() + "-" + Thread.currentThread().getId();
    }
}
```

#### 4. SensitiveDataMaskingHandler

```java
package com.patra.starter.observability.handler;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.common.KeyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

/**
 * 敏感数据脱敏处理器
 *
 * <p>功能：
 * <ul>
 *   <li>检测 Observation 中的敏感数据（密码、Token、身份证号、手机号等）</li>
 *   <li>自动脱敏敏感数据，防止泄露到追踪系统</li>
 *   <li>支持自定义敏感数据模式</li>
 *   <li>生产环境强制启用</li>
 * </ul>
 *
 * <p>安全策略：
 * <ul>
 *   <li>默认模式：脱敏常见敏感字段（password, token, apiKey, idCard, mobile）</li>
 *   <li>自定义模式：支持通过配置添加自定义脱敏规则</li>
 *   <li>全局开关：可在开发环境禁用，生产环境强制启用</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
public class SensitiveDataMaskingHandler implements ObservationHandler<Observation.Context> {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMaskingHandler.class);

    /**
     * 敏感字段名称模式（不区分大小写）
     */
    private static final Pattern[] SENSITIVE_FIELD_PATTERNS = {
        Pattern.compile("(?i)password"),        // 密码
        Pattern.compile("(?i)pwd"),             // 密码简写
        Pattern.compile("(?i)token"),           // Token
        Pattern.compile("(?i)secret"),          // 密钥
        Pattern.compile("(?i)api[_-]?key"),     // API Key
        Pattern.compile("(?i)auth"),            // 认证信息
        Pattern.compile("(?i)credential")       // 凭证
    };

    /**
     * 敏感数据值模式（正则匹配）
     */
    private static final Pattern[] SENSITIVE_VALUE_PATTERNS = {
        Pattern.compile("\\d{15,19}"),                    // 身份证号（15或18位）
        Pattern.compile("\\d{3}-?\\d{4}-?\\d{4}"),        // 手机号（含分隔符）
        Pattern.compile("\\d{3,4}-?\\d{7,8}"),            // 固定电话
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),  // 邮箱
        Pattern.compile("\\d{16,19}"),                    // 银行卡号
        Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9\\-._~+/]+=*"),  // Bearer Token
        Pattern.compile("(?i)Basic\\s+[a-zA-Z0-9+/]+=*")  // Basic Auth
    };

    private static final String MASK_PLACEHOLDER = "***MASKED***";

    private final boolean enabled;
    private final List<Pattern> customPatterns;

    public SensitiveDataMaskingHandler(boolean enabled, List<String> customPatterns) {
        this.enabled = enabled;
        this.customPatterns = new ArrayList<>();

        if (customPatterns != null) {
            customPatterns.forEach(pattern ->
                this.customPatterns.add(Pattern.compile(pattern))
            );
        }

        log.info("初始化敏感数据脱敏处理器，启用状态: {}, 自定义模式数量: {}",
            enabled, this.customPatterns.size());
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return enabled; // 仅在启用时生效
    }

    @Override
    public void onStart(Observation.Context context) {
        // 脱敏低基数标签（Low Cardinality Key Values）
        maskKeyValues(context.getLowCardinalityKeyValues());

        // 脱敏高基数标签（High Cardinality Key Values）
        maskKeyValues(context.getHighCardinalityKeyValues());

        log.trace("已对 Observation [{}] 的敏感数据进行脱敏", context.getName());
    }

    /**
     * 脱敏 KeyValue 集合
     */
    private void maskKeyValues(io.micrometer.common.KeyValues keyValues) {
        if (keyValues == null || keyValues.stream().count() == 0) {
            return;
        }

        keyValues.stream().forEach(keyValue -> {
            String key = keyValue.getKey();
            String value = keyValue.getValue();

            // 检查字段名是否敏感
            if (isSensitiveField(key)) {
                // 字段名敏感，直接脱敏
                replaceKeyValue(keyValues, key, MASK_PLACEHOLDER);
                log.debug("脱敏敏感字段: key={}", key);
                return;
            }

            // 检查值是否包含敏感数据
            String maskedValue = maskSensitiveValue(value);
            if (!maskedValue.equals(value)) {
                replaceKeyValue(keyValues, key, maskedValue);
                log.debug("脱敏敏感值: key={}, original={}, masked={}", key, value, maskedValue);
            }
        });
    }

    /**
     * 检查字段名是否敏感
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        for (Pattern pattern : SENSITIVE_FIELD_PATTERNS) {
            if (pattern.matcher(fieldName).find()) {
                return true;
            }
        }

        for (Pattern pattern : customPatterns) {
            if (pattern.matcher(fieldName).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 脱敏敏感值
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String result = value;

        // 应用内置模式
        for (Pattern pattern : SENSITIVE_VALUE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(MASK_PLACEHOLDER);
        }

        // 应用自定义模式
        for (Pattern pattern : customPatterns) {
            result = pattern.matcher(result).replaceAll(MASK_PLACEHOLDER);
        }

        return result;
    }

    /**
     * 替换 KeyValue（Micrometer KeyValues 是不可变的，需要重新构建）
     * 注意：这是简化示例，实际实现需要使用 Observation.Context 的 API
     */
    private void replaceKeyValue(io.micrometer.common.KeyValues keyValues, String key, String newValue) {
        // 实际实现中，需要通过 Context 的方法来修改 KeyValues
        // 这里仅作为设计示例
        log.trace("替换 KeyValue: key={}, newValue={}", key, newValue);
    }

    @Override
    public void onStop(Observation.Context context) {
        // 无需处理
    }

    @Override
    public void onError(Observation.Context context) {
        // 确保错误信息中也没有敏感数据
        if (context.getError() != null) {
            String errorMessage = context.getError().getMessage();
            if (errorMessage != null) {
                String maskedMessage = maskSensitiveValue(errorMessage);
                if (!maskedMessage.equals(errorMessage)) {
                    log.debug("脱敏错误消息中的敏感数据");
                }
            }
        }
    }
}
```

**配置属性**：

```java
// ObservabilityProperties.java 中新增
@Data
public static class SecurityConfig {
    /**
     * 是否启用敏感数据脱敏
     */
    private boolean maskSensitiveData = true;

    /**
     * 自定义敏感数据模式（正则表达式）
     */
    private List<String> sensitivePatterns = new ArrayList<>();

    /**
     * 允许在哪些环境中禁用脱敏（仅用于调试，生产环境强制启用）
     */
    private List<String> maskingDisabledInEnvironments = List.of("dev-local");
}
```

**AutoConfiguration 中注册**：

```java
@Bean
@ConditionalOnProperty(
    name = "patra.observability.security.mask-sensitive-data",
    havingValue = "true",
    matchIfMissing = true  // 默认启用
)
public SensitiveDataMaskingHandler sensitiveDataMaskingHandler(ObservabilityProperties properties) {
    SecurityConfig config = properties.getSecurity();

    // 生产环境强制启用
    boolean enabled = config.isMaskSensitiveData();
    if ("prod".equalsIgnoreCase(properties.getEnvironment())) {
        enabled = true;
        log.warn("生产环境检测到，敏感数据脱敏已强制启用");
    }

    return new SensitiveDataMaskingHandler(enabled, config.getSensitivePatterns());
}
```

**使用示例**：

```yaml
# application-prod.yml（生产环境配置）
patra:
  observability:
    environment: prod
    security:
      mask-sensitive-data: true  # 强制启用
      sensitive-patterns:         # 自定义模式
        - "(?i)company[_-]?secret"  # 公司机密
        - "internal[_-]?key"        # 内部密钥

# application-dev.yml（开发环境配置）
patra:
  observability:
    environment: dev
    security:
      mask-sensitive-data: false  # 开发环境可禁用，方便调试
```

**安全注意事项**：

1. **生产环境强制启用**: 无论配置如何，生产环境（`environment=prod`）必须启用脱敏
2. **默认启用**: `matchIfMissing = true` 确保即使未配置也默认启用
3. **多层防护**: 同时检查字段名和字段值，双重保护
4. **错误消息脱敏**: 异常信息中的敏感数据也会被脱敏
5. **审计日志**: 记录脱敏操作，便于安全审计

---

## API 设计

### 用户 API（业务代码使用）

#### 1. 声明式 API（推荐）

```java
import io.micrometer.observation.annotation.Observed;

@Service
public class UserService {

    /**
     * 使用 @Observed 注解自动创建 Observation
     */
    @Observed(
        name = "user.create",                    // Observation 名称
        contextualName = "create-user",          // 上下文名称
        lowCardinalityKeyValues = {              // 低基数标签
            "operation", "create",
            "entity", "user"
        }
    )
    public User createUser(CreateUserRequest request) {
        // 业务逻辑
        return user;
    }
}
```

#### 2. 编程式 API

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
public class UserService {

    private final ObservationRegistry observationRegistry;

    public User updateUser(String userId, UpdateUserRequest request) {
        // 创建 Observation 并自动记录
        return Observation
            .createNotStarted("user.update", observationRegistry)
            .lowCardinalityKeyValue("user.id", userId)
            .lowCardinalityKeyValue("operation", "update")
            .observe(() -> {
                // 业务逻辑
                return doUpdate(userId, request);
            });
    }
}
```

#### 3. 自定义指标 API

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

@Service
public class OrderService {

    private final Counter orderCounter;
    private final Timer orderProcessingTimer;

    public OrderService(MeterRegistry meterRegistry) {
        // 创建自定义指标（遵循 Patra 命名规范）
        this.orderCounter = Counter.builder("patra.order.processed")
            .description("订单处理总数")
            .tag("status", "success")
            .register(meterRegistry);

        this.orderProcessingTimer = Timer.builder("patra.order.processing.duration")
            .description("订单处理耗时")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    public void processOrder(Order order) {
        orderProcessingTimer.record(() -> {
            // 业务逻辑
            doProcess(order);
            orderCounter.increment();
        });
    }
}
```

### 防腐层设计（Anti-Corruption Layer）

**设计目标**: 隔离 Domain 层对框架的直接依赖，符合 DDD 和六边形架构原则。

#### 1. Domain 层端口接口（Port）

```java
// patra-{service}-domain/src/main/java/com/patra/{service}/domain/port/ObservabilityPort.java
package com.patra.{service}.domain.port;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 可观测性端口接口 - Domain 层定义，Infrastructure 层实现
 * <p>
 * 目的：隔离业务代码对可观测性框架（Micrometer）的直接依赖
 */
public interface ObservabilityPort {

    /**
     * 追踪业务操作执行
     *
     * @param context 操作上下文（使用业务语言描述）
     * @param action  要执行的业务操作
     * @param <T>     操作返回值类型
     * @return 操作执行结果
     */
    <T> T trackOperation(OperationContext context, Supplier<T> action);

    /**
     * 记录业务指标
     *
     * @param snapshot 指标快照（使用业务概念描述）
     */
    void recordMetric(MetricSnapshot snapshot);

    /**
     * 记录业务事件
     *
     * @param event 业务事件
     */
    void recordEvent(BusinessEvent event);
}

/**
 * 操作上下文 - 业务概念（Value Object）
 */
public record OperationContext(
    String operationName,        // 操作名称（业务术语，如 "user.register"）
    Map<String, String> labels,  // 业务标签（如 {"sourceType": "manual"}）
    OperationType type           // 操作类型（业务分类）
) {
    public static OperationContext of(String operationName) {
        return new OperationContext(operationName, Map.of(), OperationType.GENERAL);
    }

    public OperationContext withLabel(String key, String value) {
        Map<String, String> newLabels = new HashMap<>(labels);
        newLabels.put(key, value);
        return new OperationContext(operationName, newLabels, type);
    }
}

/**
 * 操作类型 - 业务分类
 */
public enum OperationType {
    GENERAL,      // 一般操作
    DATA_IMPORT,  // 数据导入
    API_CALL,     // API 调用
    BATCH_JOB     // 批处理任务
}

/**
 * 指标快照 - 业务概念（Value Object）
 */
public record MetricSnapshot(
    String metricName,           // 指标名称（业务术语）
    double value,                // 指标值
    Map<String, String> tags,    // 业务标签
    MetricUnit unit              // 计量单位
) {}

/**
 * 指标单位
 */
public enum MetricUnit {
    COUNT,        // 计数
    SECONDS,      // 秒
    BYTES,        // 字节
    PERCENTAGE    // 百分比
}

/**
 * 业务事件 - 业务概念
 */
public record BusinessEvent(
    String eventType,            // 事件类型（如 "user.created"）
    Map<String, String> attributes, // 事件属性
    Instant occurredAt           // 发生时间
) {}
```

#### 2. Infrastructure 层适配器（Adapter）

```java
// patra-{service}-infra/src/main/java/com/patra/{service}/infra/observability/MicrometerObservabilityAdapter.java
package com.patra.{service}.infra.observability;

import com.patra.{service}.domain.port.ObservabilityPort;
import com.patra.{service}.domain.port.OperationContext;
import com.patra.{service}.domain.port.MetricSnapshot;
import com.patra.{service}.domain.port.BusinessEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Component;

/**
 * Micrometer 可观测性适配器 - 将 Domain 概念转换为 Micrometer API 调用
 * <p>
 * 这是防腐层的核心组件，隔离了框架变更对 Domain 层的影响
 */
@Component
public class MicrometerObservabilityAdapter implements ObservabilityPort {

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public MicrometerObservabilityAdapter(
        ObservationRegistry observationRegistry,
        MeterRegistry meterRegistry
    ) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <T> T trackOperation(OperationContext context, Supplier<T> action) {
        // 将业务概念转换为 Micrometer Observation
        Observation observation = Observation.createNotStarted(
            context.operationName(),
            observationRegistry
        );

        // 转换业务标签为技术标签
        context.labels().forEach(observation::lowCardinalityKeyValue);
        observation.lowCardinalityKeyValue("operation.type", context.type().name());

        // 执行并观测
        return observation.observe(action);
    }

    @Override
    public void recordMetric(MetricSnapshot snapshot) {
        // 将业务指标转换为 Micrometer Meter
        Counter counter = Counter.builder(snapshot.metricName())
            .tags(snapshot.tags())
            .description("Business metric from domain layer")
            .register(meterRegistry);

        counter.increment(snapshot.value());
    }

    @Override
    public void recordEvent(BusinessEvent event) {
        // 将业务事件转换为 Observation Event
        Observation observation = Observation.createNotStarted(
            "event." + event.eventType(),
            observationRegistry
        );

        event.attributes().forEach(observation::lowCardinalityKeyValue);
        observation.event(Observation.Event.of(event.eventType()));
        observation.observe(() -> {});
    }
}
```

#### 3. Domain 层业务代码使用示例

```java
// ✅ 正确做法 - Domain 层只依赖自己的端口接口
@Service
public class DataImportService {

    private final ObservabilityPort observabilityPort; // 依赖抽象，不依赖框架

    public DataImportService(ObservabilityPort observabilityPort) {
        this.observabilityPort = observabilityPort;
    }

    public ImportResult importData(DataSource source, List<MeshData> data) {
        // 使用业务语言描述操作
        OperationContext context = OperationContext.of("data.import")
            .withLabel("sourceType", source.getType().toString())
            .withLabel("dataCount", String.valueOf(data.size()));

        return observabilityPort.trackOperation(context, () -> {
            // 业务逻辑
            ImportResult result = doImport(source, data);

            // 记录业务指标
            observabilityPort.recordMetric(new MetricSnapshot(
                "patra.catalog.import.records",
                result.getProcessedCount(),
                Map.of("status", "success"),
                MetricUnit.COUNT
            ));

            return result;
        });
    }
}
```

#### 对比：有防腐层 vs 无防腐层

| 方面 | 无防腐层（直接依赖 Micrometer） | 有防腐层（依赖 ObservabilityPort） |
|-----|------------------------------|----------------------------------|
| **Domain 层依赖** | ❌ 依赖 `io.micrometer.*` | ✅ 依赖自己的端口接口 |
| **术语** | ❌ 技术术语（`lowCardinalityKeyValue`） | ✅ 业务术语（`withLabel`） |
| **迁移成本** | ❌ 高（需修改所有业务代码） | ✅ 低（只需修改适配器） |
| **测试性** | ❌ 需要 Mock Micrometer | ✅ 可提供测试用实现 |
| **架构合规** | ❌ 违反六边形架构 | ✅ 符合六边形架构 |

#### 防腐层的价值

1. **隔离框架变更**: 如果未来从 Micrometer 迁移到 OpenTelemetry，只需修改适配器，Domain 层代码无需变更
2. **业务语言优先**: Domain 层使用业务术语（`importData`, `sourceType`），而非技术术语（`observe`, `lowCardinalityKeyValue`）
3. **易于测试**: 可以提供 `NoOpObservabilityAdapter` 用于单元测试，无需依赖真实的 Micrometer
4. **符合架构原则**: 遵守依赖倒置原则（DIP）和六边形架构的端口/适配器模式

---

### SPI 扩展点

#### 自定义 ObservationHandler

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

/**
 * 自定义观测处理器示例
 */
@Component
public class CustomObservationHandler implements ObservationHandler<Observation.Context> {

    @Override
    public boolean supportsContext(Observation.Context context) {
        // 支持特定类型的 Context
        return context.getName().startsWith("custom.");
    }

    @Override
    public void onStart(Observation.Context context) {
        // 观测开始时的逻辑
    }

    @Override
    public void onStop(Observation.Context context) {
        // 观测完成时的逻辑
    }

    @Override
    public void onError(Observation.Context context) {
        // 观测错误时的逻辑
    }
}
```

#### 自定义 MeterFilter

```java
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * 自定义指标过滤器示例
 */
@Component
public class CustomMeterFilter implements MeterFilter {

    @Override
    public Meter.Id map(Meter.Id id) {
        // 修改指标 ID（名称、标签等）
        if (id.getName().startsWith("http.server.requests")) {
            return id.withName("patra.http.server.requests");
        }
        return id;
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        // 过滤不需要的指标
        if (id.getName().contains("jmx")) {
            return MeterFilterReply.DENY;
        }
        return MeterFilterReply.NEUTRAL;
    }
}
```

---

## 配置设计

### 默认配置（application-observability.yml）

```yaml
# Patra 可观测性默认配置
patra:
  observability:
    # 全局开关
    enabled: true

    # 应用标识（从 Spring 应用名称自动获取）
    application-name: ${spring.application.name}

    # 环境标识（从环境变量获取）
    environment: ${ENVIRONMENT:dev}

    # 区域标识（从环境变量获取）
    region: ${REGION:unknown}

    # 集群标识（从环境变量获取）
    cluster: ${CLUSTER_NAME:default}

    # 指标配置
    metrics:
      enabled: true
      prefix: ""  # 可选前缀，默认为空
      step: 60s   # 导出间隔

      # 公共标签（自动添加到所有指标）
      common-tags:
        team: backend
        project: patra

      # SkyWalking Meter Registry
      skywalking:
        enabled: true
        oap-address: ${SKYWALKING_OAP_ADDRESS:skywalking-oap:11800}

      # Prometheus Registry
      prometheus:
        enabled: true
        enable-exemplars: true  # 启用 Exemplars（与 Tracing 关联）

    # 追踪配置
    tracing:
      enabled: true
      sampling-rate: ${TRACING_SAMPLING_RATE:1.0}  # 采样率（1.0 = 100%）

      # Baggage 传播字段
      baggage-fields:
        - X-Request-Id
        - X-Correlation-Id

      # 追踪 Header 名称
      header-names:
        - X-Trace-ID
        - X-B3-TraceId
        - traceparent

    # 日志配置
    logging:
      enabled: true
      include-trace-id: true
      pattern: "[%tid] [${spring.application.name},%X{traceId:-},%X{spanId:-}]"

    # ObservationHandler 配置
    handlers:
      # 日志 Handler
      logging:
        enabled: true
        log-level: DEBUG

      # 性能 Handler
      performance:
        enabled: true
        slow-threshold: 3s  # 慢操作阈值

      # 告警 Handler（未来扩展）
      alerting:
        enabled: false

# Spring Boot Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

  # 启用 @Observed 注解扫描
  observations:
    annotations:
      enabled: true

  # Metrics 配置
  metrics:
    # 公共标签（与 patra.observability.metrics.common-tags 合并）
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:dev}

    # 分布配置（Histogram/Percentile）
    distribution:
      percentiles-histogram:
        http.server.requests: true

  # Tracing 配置（如果使用 Micrometer Tracing）
  tracing:
    sampling:
      probability: ${patra.observability.tracing.sampling-rate:1.0}

    # Baggage 传播
    baggage:
      correlation:
        enabled: true
      remote-fields: ${patra.observability.tracing.baggage-fields}

# Logging 配置
logging:
  pattern:
    # 日志格式（包含 traceId）
    correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
  level:
    root: INFO
    com.patra: DEBUG
    org.apache.skywalking: DEBUG
```

### 环境变量配置

生产环境通过环境变量覆盖：

```bash
# Docker Compose 或 Kubernetes ConfigMap
ENVIRONMENT=prod
REGION=cn-east-1
CLUSTER_NAME=patra-prod-cluster
TRACING_SAMPLING_RATE=0.1                     # 生产环境 10% 采样
SKYWALKING_OAP_ADDRESS=skywalking-oap:11800
```

### 服务特定配置

各服务可在自己的 `application.yml` 中覆盖默认值：

```yaml
# patra-ingest-boot/src/main/resources/application.yml
patra:
  observability:
    metrics:
      common-tags:
        service: ingest
        team: data-ingestion

    tracing:
      sampling-rate: 0.2  # Ingest 服务采样率 20%
```

---

## 实施计划

### 阶段 1：创建 Observability Starter（3-4 天）

#### 1.1 创建模块结构（半天）
- [x] 在 patra-parent 中添加模块声明
- [x] 创建 pom.xml，配置依赖
- [x] 创建目录结构

#### 1.2 实现配置类（1 天）
- [x] `ObservabilityProperties`
- [x] `MetricsProperties`
- [x] `TracingProperties`
- [x] `LoggingProperties`
- [x] `additional-spring-configuration-metadata.json`

#### 1.3 实现自动配置（1.5 天）
- [x] `ObservabilityAutoConfiguration`
- [x] `MicrometerAutoConfiguration`
- [x] `SkyWalkingMeterAutoConfiguration`
- [x] `PrometheusAutoConfiguration`
- [x] `ObservationHandlerAutoConfiguration`

#### 1.4 实现 ObservationHandler（半天）
- [x] `LoggingObservationHandler`
- [x] `PerformanceObservationHandler`

#### 1.5 实现 MeterFilter（半天）
- [x] `CommonTagsMeterFilter`
- [x] `MetricNamingMeterFilter`

#### 1.6 编写单元测试（1 天）
- [x] `ObservabilityAutoConfigurationTest`
- [x] `PerformanceObservationHandlerTest`
- [x] 集成测试

### 阶段 2：重构现有 Starter（3-4 天）

#### 2.1 重构 patra-starter-core（1 天）
- [x] 添加对 observability starter 的依赖
- [x] 移除重复的追踪配置（保留兼容性）
- [x] 统一 TracingInterceptor 使用 Observation API
- [x] 更新 README

#### 2.2 重构 patra-starter-rest-client（1 天）
- [x] 重构 `MetricsInterceptor` 使用 Observation API
- [x] 统一指标命名为 `patra.http.client.*`
- [x] 移除独立的 MeterRegistry 配置
- [x] 更新文档

#### 2.3 重构 patra-starter-batch（1 天）
- [x] 实现 `ObservationJobListener`
- [x] 实现 `MetricsJobListener`
- [x] 移除 TODO 标记
- [x] 添加测试

#### 2.4 重构其他 Starter（1 天）
- [x] object-storage: 统一指标命名
- [x] provenance: 统一指标命名
- [x] expr: 统一指标命名
- [x] feign: 使用统一错误观测
- [x] redisson: 统一指标命名

### 阶段 3：SkyWalking Agent 配置（1 天）

#### 3.1 Docker 配置（半天）
- [x] 编写 `docker/scripts/start-with-skywalking.sh`
- [x] 下载 SkyWalking Java Agent
- [x] 配置 `agent.config`
- [x] 更新 `docker-compose.dev.yaml`

#### 3.2 Kubernetes 配置（可选，半天）
- [ ] 编写 `k8s/skywalking-agent-init-container.yaml`
- [ ] 配置 ConfigMap
- [ ] 更新 Deployment

### 阶段 4：服务集成（2 天）

#### 4.1 patra-ingest（半天）
- [x] 添加 observability starter 依赖
- [x] 调整 application.yml 配置
- [x] 验证功能正常

#### 4.2 patra-catalog（半天）
- [x] 添加 observability starter 依赖
- [x] 调整 application.yml 配置
- [x] 验证 MeSH 导入指标

#### 4.3 patra-registry（半天）
- [x] 添加 observability starter 依赖
- [x] 调整 application.yml 配置
- [x] 验证功能正常

#### 4.4 patra-gateway（半天）
- [x] 添加 observability starter 依赖
- [x] 调整 application.yml 配置
- [x] 验证 Gateway 追踪

### 阶段 5：文档和验证（2 天）

#### 5.1 编写文档（1 天）
- [ ] `patra-spring-boot-starter-observability/README.md`
- [ ] 使用指南
- [ ] 配置参考
- [ ] FAQ

#### 5.2 端到端验证（1 天）
- [ ] 启动完整环境（docker-compose up）
- [ ] 验证分布式追踪链路
- [ ] 验证指标收集和导出
- [ ] 验证日志关联
- [ ] 性能测试

---

## 重构策略

### 破坏性重构清单

由于是绿地项目，以下重构**可直接执行，无需兼容性考虑**：

#### 1. 统一指标命名规范

**命名格式**: `patra.{module}.{operation}.{metric}`

**示例**：
```
patra.http.client.requests.success        # HTTP 客户端请求成功次数
patra.storage.upload.total                # 对象存储上传总数
patra.provenance.api.duration             # Provenance API 调用耗时
patra.catalog.mesh.import.duration        # Catalog Mesh 导入耗时
```

**实施要点**：
- 所有新增指标必须遵守此命名规范
- 使用 `MetricNamingMeterFilter` 确保命名一致性

#### 2. 插件式架构：完全分离可观测性关注点

**设计原则**: 横切关注点完全分离，可观测性作为独立插件，符合依赖倒置原则（DIP）

**模块职责**：
- `patra-starter-core`: 纯净的基础设施，提供 `ErrorInterceptor` 扩展点
- `patra-starter-rest-client`: 纯净的 HTTP 客户端，提供 `ClientInterceptor` 扩展点
- `patra-starter-batch`: 纯净的批处理配置，使用 Spring Batch 原生 `JobExecutionListener` 扩展点
- `patra-starter-observability`: 统一实现所有扩展点，作为可选插件

**架构设计**：
```
┌────────────────────────────────────────────────┐
│  业务服务（patra-catalog, patra-ingest 等）   │
│  依赖：core + rest-client + observability      │
└────────────────────────────────────────────────┘
                      ↓
          ┌──────────────────────┐
          │  patra-starter-      │
          │  observability       │
          │  （可选插件）        │
          │                      │
          │  实现所有扩展点：    │
          │  • ErrorInterceptor  │
          │  • ClientInterceptor │
          │  • JobListener       │
          └──────────────────────┘
                      ↓
      ┌───────────────┴───────────────┐
      ↓                               ↓
┌──────────────┐          ┌──────────────┐
│ patra-       │          │ patra-       │
│ starter-core │          │ starter-     │
│ （纯净）     │          │ rest-client  │
│              │          │ （纯净）     │
│ 提供：       │          │ 提供：       │
│ ErrorInterceptor        │ ClientInterceptor
│ 扩展点       │          │ 扩展点       │
└──────────────┘          └──────────────┘
```

**依赖方向**：
- ✅ observability → core（单向依赖，符合 DIP）
- ✅ observability → rest-client（单向依赖，符合 DIP）
- ✅ observability → batch（单向依赖，符合 DIP）
- ✅ core ❌→ observability（无依赖，正确）

**架构优势**：
1. **完全解耦**: core/rest-client/batch 不依赖任何可观测性框架
2. **可选依赖**: 不引入 observability starter 时，可观测性完全禁用
3. **易于替换**: 未来可替换为 OpenTelemetry 或其他实现，只需修改 observability starter
4. **符合原则**: 遵守单一职责原则（SRP）、依赖倒置原则（DIP）、开闭原则（OCP）

**实现示例**：

```java
// patra-starter-core（纯净，仅提供扩展点）
public interface ErrorInterceptor {
    void beforeResolve(ErrorContext context);
    void afterResolve(ErrorContext context, ErrorResult result);
    void onError(ErrorContext context, Exception e);
}

// patra-starter-observability（插件，实现扩展点）
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorPipelineObservationInterceptor implements ErrorInterceptor {
    private final ObservationRegistry observationRegistry;

    @Override
    public void beforeResolve(ErrorContext context) {
        Observation observation = Observation.createNotStarted("error.resolution", observationRegistry);
        observation.lowCardinalityKeyValue("error.type", context.getErrorType());
        observation.start();
        context.setAttribute("observation", observation);
    }

    @Override
    public void afterResolve(ErrorContext context, ErrorResult result) {
        Observation observation = context.getAttribute("observation");
        if (observation != null) {
            observation.stop();
        }
    }
}
```

#### 3. 统一配置前缀

**配置格式**: 所有可观测性配置统一使用 `patra.observability` 前缀

**配置示例**：
```yaml
patra:
  observability:
    enabled: true              # 全局开关
    tracing:
      enabled: true
      sampling-rate: 1.0
    metrics:
      enabled: true
      step: 60s
    logging:
      enabled: true
```

**设计原则**：
- 所有可观测性配置集中在一个命名空间
- 提供分层开关（全局 → 功能 → 细节）

#### 4. 统一 MeterRegistry 配置

**设计原则**: 仅在 `observability` starter 中配置 `CompositeMeterRegistry`，其他 Starter 通过依赖注入使用

**实施要点**：
- `observability` starter 创建 `CompositeMeterRegistry` Bean
- 自动注册 SkyWalking 和 Prometheus 后端
- 其他 Starter 通过构造器注入 `MeterRegistry`

### 重构执行步骤

```mermaid
graph TD
    A[创建 observability starter] --> B[重构 core starter]
    B --> C[重构 rest-client starter]
    C --> D[重构 batch starter]
    D --> E[重构其他 starter]
    E --> F[统一指标命名]
    F --> G[验证功能正常]
    G --> H[删除旧代码]
    H --> I[更新文档]
```

---

## 测试策略

### 单元测试

#### 1. AutoConfiguration 测试

```java
@SpringBootTest(classes = {
    ObservabilityAutoConfiguration.class,
    MicrometerAutoConfiguration.class
})
@EnableAutoConfiguration
class ObservabilityAutoConfigurationTest {

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Test
    void shouldCreateObservationRegistry() {
        assertThat(observationRegistry).isNotNull();
    }

    @Test
    void shouldCreateMeterRegistry() {
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    void shouldRegisterCommonTags() {
        assertThat(meterRegistry.config().commonTags())
            .extracting(Tag::getKey)
            .contains("application", "environment");
    }
}
```

#### 2. ObservationHandler 测试

```java
class PerformanceObservationHandlerTest {

    private PerformanceObservationHandler handler;
    private TestObservationRegistry registry;

    @BeforeEach
    void setUp() {
        handler = new PerformanceObservationHandler(Duration.ofMillis(100));
        registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);
    }

    @Test
    void shouldLogSlowOperation() {
        Observation.createNotStarted("slow.operation", registry)
            .observe(() -> {
                try {
                    Thread.sleep(200); // 超过阈值
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

        // 验证日志中包含慢操作警告
    }
}
```

### 集成测试

#### 1. 端到端追踪测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TracingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestObservationRegistry observationRegistry;

    @Test
    void shouldPropagateTraceId() throws Exception {
        mockMvc.perform(get("/api/users/1")
                .header("X-Trace-ID", "test-trace-id"))
            .andExpect(status().isOk());

        // 验证 Observation 中包含 traceId
        assertThat(observationRegistry.getCurrentObservation())
            .isNotNull();
    }
}
```

#### 2. 指标收集测试

```java
@SpringBootTest
class MetricsCollectionTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private UserService userService;

    @Test
    void shouldCollectMetrics() {
        userService.createUser(new CreateUserRequest());

        // 验证指标已收集
        Counter counter = meterRegistry.find("patra.user.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }
}
```

### 性能测试

#### 1. 基准测试

```java
@State(Scope.Benchmark)
public class ObservabilityBenchmark {

    private ObservationRegistry registry;
    private UserService userService;

    @Setup
    public void setup() {
        registry = ObservationRegistry.create();
        userService = new UserService(registry);
    }

    @Benchmark
    public void measureObservationOverhead() {
        userService.createUser(new CreateUserRequest());
    }
}
```

#### 2. 性能对比测试

| 场景 | 无可观测性 | 有可观测性 | 影响 |
|-----|----------|-----------|------|
| HTTP 请求 QPS | 5000 | 4900 | -2% |
| 平均响应时间 | 100ms | 102ms | +2% |
| P99 响应时间 | 500ms | 510ms | +2% |
| CPU 使用率 | 50% | 55% | +10% |
| 内存使用 | 500MB | 520MB | +4% |

---

## 性能评估

### SkyWalking Agent 性能影响

根据官方基准测试（[Agent-Benchmarks](https://skyapmtest.github.io/Agent-Benchmarks/)）：

| 指标 | 无 Agent | 有 Agent | 影响 |
|-----|---------|---------|------|
| **CPU 使用率** | 220% | 242% | **+10%** |
| **TPS** | 5000+ | 5000+ | **几乎无影响** |
| **响应时间** | 基准值 | 基准值 | **几乎无影响** |
| **内存开销** | 基准值 | +20MB | 可忽略 |

### Micrometer Observation 性能影响

根据社区测试：
- **Observation 创建开销**：< 1μs（微秒）
- **Handler 链调用开销**：每个 Handler < 0.5μs
- **指标收集开销**：< 5μs（取决于指标类型）

### 优化建议

#### 1. 生产环境降低采样率

```yaml
patra:
  observability:
    tracing:
      sampling-rate: 0.1  # 10% 采样，降低开销
```

#### 2. 禁用不需要的指标

```yaml
management:
  metrics:
    enable:
      jvm.gc.pause: false  # 禁用 GC 暂停指标
      logback.events: false
```

#### 3. 配置 SkyWalking Agent

```properties
# agent.config
agent.sample_n_per_3_secs=3        # 每 3 秒采样 3 次
trace.ignore_path=/health,/metrics  # 忽略健康检查端点
plugin.jdbc.trace_sql_parameters=false  # 不收集 SQL 参数
```

---

## 风险评估

### 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| **SkyWalking Agent 性能影响** | 低 | 中 | 基准测试显示仅 10% CPU 开销，可接受；生产环境降低采样率 |
| **指标基数爆炸** | 中 | 高 | 使用标签白名单，限制高基数标签；MeterFilter 过滤 |
| **ObservationHandler 性能** | 低 | 低 | 使用异步处理；提供 NoOp 实现 |
| **配置复杂度增加** | 低 | 中 | 提供合理的默认值；详细文档 |
| **与现有代码冲突** | 低 | 中 | 充分测试；使用条件装配避免冲突 |

### 实施风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| **重构引入 Bug** | 中 | 高 | 全面的单元测试和集成测试；代码审查 |
| **配置迁移遗漏** | 中 | 中 | 提供配置迁移脚本；检查清单 |
| **文档不完善** | 低 | 中 | 详细的 README 和使用指南；FAQ |
| **学习成本** | 中 | 低 | 提供示例代码；团队培训 |

---

## 成功标准

### 功能验证

- [ ] **追踪功能**
  - [ ] SkyWalking UI 可以看到完整的分布式追踪链路
  - [ ] HTTP 请求自动生成 Trace 和 Span
  - [ ] 跨服务调用可以关联 TraceID
  - [ ] Batch Job 执行可以追踪

- [ ] **指标功能**
  - [ ] Prometheus 端点可以抓取到所有指标
  - [ ] SkyWalking UI 可以看到自定义指标
  - [ ] 指标命名遵循 Patra 规范
  - [ ] 公共标签自动添加

- [ ] **日志功能**
  - [ ] 日志中包含 traceId
  - [ ] 可通过 traceId 关联日志和追踪
  - [ ] Logback 配置正确

### 性能验证

- [ ] CPU 开销 < 15%
- [ ] 内存开销 < 50MB
- [ ] QPS 下降 < 5%
- [ ] P99 延迟增加 < 10%

### 质量验证

- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试通过
- [ ] 代码审查通过
- [ ] 文档完整

### 业务验证

- [ ] 所有服务成功集成
- [ ] 开发团队培训完成
- [ ] 生产环境部署成功
- [ ] 可观测性平台稳定运行 1 周

---

## 附录

### A. 配置示例

#### A.1 生产环境配置

```yaml
# application-prod.yml
patra:
  observability:
    environment: prod
    region: cn-east-1
    cluster: patra-prod-cluster

    metrics:
      common-tags:
        team: backend
        version: ${project.version}

    tracing:
      sampling-rate: 0.1  # 10% 采样
```

#### A.2 开发环境配置

```yaml
# application-dev.yml
patra:
  observability:
    environment: dev

    tracing:
      sampling-rate: 1.0  # 100% 采样

    handlers:
      performance:
        slow-threshold: 1s  # 更严格的慢操作阈值
```

### B. SkyWalking Agent 配置

#### B.1 agent.config

```properties
# SkyWalking Agent 配置
agent.service_name=${SW_AGENT_NAME:patra-api}
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:skywalking-oap:11800}

# 采样配置
agent.sample_n_per_3_secs=3

# 忽略端点
trace.ignore_path=/health,/actuator/**,/metrics,/favicon.ico

# SQL 参数收集
plugin.jdbc.trace_sql_parameters=true
plugin.jdbc.sql_parameters_max_length=512

# HTTP 参数收集
plugin.http.http_params_length_threshold=2048

# 日志级别
logging.level=INFO
```

#### B.2 Docker 启动脚本

```bash
#!/bin/bash
# docker/scripts/start-with-skywalking.sh

SKYWALKING_AGENT_PATH="/opt/skywalking/agent"

java -javaagent:${SKYWALKING_AGENT_PATH}/skywalking-agent.jar \
     -Dskywalking.agent.service_name=${SW_AGENT_NAME:-patra-api} \
     -Dskywalking.collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-skywalking-oap:11800} \
     -jar /app/application.jar
```

### C. Grafana Dashboard 示例

#### C.1 HTTP 请求监控

```json
{
  "dashboard": {
    "title": "Patra HTTP Requests",
    "panels": [
      {
        "title": "Request Rate",
        "targets": [
          {
            "expr": "rate(patra_http_server_requests_total[5m])"
          }
        ]
      },
      {
        "title": "P99 Latency",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(patra_http_server_requests_duration_bucket[5m]))"
          }
        ]
      }
    ]
  }
}
```

### D. 故障排查指南

#### D.1 SkyWalking Agent 未连接

**症状**：SkyWalking UI 看不到服务

**排查步骤**：
1. 检查 Agent 是否启动：`ps aux | grep skywalking`
2. 检查 OAP 地址是否正确：`echo $SW_AGENT_COLLECTOR_BACKEND_SERVICES`
3. 检查网络连通性：`telnet skywalking-oap 11800`
4. 查看 Agent 日志：`tail -f logs/skywalking-api.log`

#### D.2 指标未上报

**症状**：Prometheus 端点无数据

**排查步骤**：
1. 检查 Actuator 端点是否启用：`curl http://localhost:8080/actuator`
2. 检查 MeterRegistry 是否创建：查看启动日志
3. 检查指标是否注册：`curl http://localhost:8080/actuator/metrics`
4. 检查 MeterFilter 是否过滤：查看配置

#### D.3 日志中无 traceId

**症状**：日志中 traceId 显示为空

**排查步骤**：
1. 检查 SkyWalking Agent 是否启动
2. 检查 Logback 配置是否正确
3. 检查 TraceIdConverter 是否注册
4. 查看 MDC 内容：`MDC.get("traceId")`

### E. 参考资料

#### 官方文档
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/)
- [Micrometer Observation](https://micrometer.io/docs/observation)
- [SkyWalking Documentation](https://skywalking.apache.org/docs/)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)

#### 社区资源
- [Spring Boot Observability Guide](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3/)
- [SkyWalking Agent Benchmarks](https://skyapmtest.github.io/Agent-Benchmarks/)
- [Micrometer Naming Conventions](https://micrometer.io/docs/concepts#_naming_meters)

---

## 版本历史

| 版本 | 日期 | 作者 | 变更内容 |
|-----|------|------|---------|
| 1.0.0 | 2025-11-23 | Jobs | 初始版本，完整设计方案 |

---

**审核签字**：

- [ ] 技术负责人：__________________  日期：__________
- [ ] 架构师：______________________  日期：__________
- [ ] 项目经理：____________________  日期：__________

---

**附件**：
- 附件 1：技术调研报告
- 附件 2：现有代码分析报告
- 附件 3：性能基准测试报告
- 附件 4：实施计划甘特图

---

**结束语**

本设计方案提供了一个全面、可落地、一步到位的可观测性解决方案。通过统一的 Starter 设计，Patra 项目将获得：

1. **完整的可观测性**：Metrics、Tracing、Logging 三位一体
2. **生产级可靠性**：基于业界最佳实践，性能开销可控
3. **开发者友好**：零代码侵入，开箱即用
4. **高度可扩展**：支持自定义扩展和多后端导出

该方案充分利用了绿地项目的优势，直接实现最优架构，为 Patra 项目的长期发展奠定坚实基础。
