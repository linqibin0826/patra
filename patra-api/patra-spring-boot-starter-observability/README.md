# Patra Spring Boot Starter - Observability

可观测性 Starter，统一集成 Metrics、Tracing、Logging 三大支柱。

## 架构说明

本模块采用 **OTel Agent + Micrometer 混合架构**：

| 支柱 | 技术方案 | 说明 |
|------|----------|------|
| **Tracing** | OTel Java Agent | 通过 `-javaagent` 参数启动，零代码侵入，自动 instrument HTTP/JDBC/Redis/MQ |
| **Metrics** | Micrometer + Prometheus | 应用代码使用 Micrometer API，Prometheus 定期 Pull `/actuator/prometheus` |
| **Logging** | Agent MDC 注入 | Agent 自动将 `trace_id`/`span_id` 注入日志上下文 |

> **为什么选择 Agent 模式？**
> - 零代码侵入：无需修改应用代码即可获得完整的分布式追踪
> - 自动覆盖：HTTP、JDBC、Redis、消息队列等基础设施自动 instrument
> - 统一标准：与 OTel Collector 原生集成，避免 SDK 版本兼容问题

## 核心功能

### 自动配置

| 配置类 | 功能 | 条件 |
|--------|------|------|
| `ObservabilityAutoConfiguration` | 核心配置，创建 ObservationRegistry，启用 @Observed 注解 | 默认启用 |
| `MicrometerAutoConfiguration` | 注册 MeterFilter（命名规范、公共标签、高基数过滤） | metrics.enabled=true |
| `PrometheusAutoConfiguration` | 配置 Prometheus Registry 和 Exemplars | prometheus.enabled=true |
| `ObservationInterceptorsAutoConfiguration` | 注册拦截器（错误解析） | 默认启用 |

### MeterFilter

| Filter | 功能 | 执行顺序 |
|--------|------|----------|
| `HighCardinalityMeterFilter` | 过滤高基数标签（userId、traceId 等），防止时序爆炸 | HIGHEST_PRECEDENCE |
| `MetricNamingMeterFilter` | 强制执行命名规范（patra.{module}.{metric}） | HIGHEST_PRECEDENCE + 1 |
| `CommonTagsMeterFilter` | 添加公共标签到所有 Meter | LOWEST_PRECEDENCE |

### 拦截器

| 拦截器 | 功能 | 适用场景 |
|--------|------|----------|
| `ObservationResolutionInterceptor` | 为错误解析流程创建 Observation | 错误处理 |

> **注意**：
> - Tracing 由 OTel Java Agent 自动处理，无需额外的 ObservationHandler
> - 批处理可观测性已迁移至 `patra-spring-boot-starter-batch`，使用 Spring Batch 原生 `BatchObservabilityBeanPostProcessor`
> - HTTP 客户端观测由 Spring Boot 3.x 内置的 `RestClient.Builder` 自动配置处理

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-observability</artifactId>
</dependency>
```

### 2. 配置 OTel Agent（启动参数）

```bash
java -javaagent:/path/to/opentelemetry-javaagent.jar \
     -Dotel.service.name=my-service \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -Dotel.traces.sampler=parentbased_traceidratio \
     -Dotel.traces.sampler.arg=0.1 \
     -jar my-app.jar
```

### 3. 配置属性

```yaml
patra:
  observability:
    enabled: true
    application-name: ${spring.application.name}
    environment: dev
    region: cn-north
    cluster: default

    metrics:
      enabled: true
      prefix: ""
      common-tags:
        team: platform
        version: ${project.version}

      prometheus:
        enabled: true
        enable-exemplars: true  # 与 Tracing 关联

    logging:
      enabled: true
      include-trace-id: true
```

### 4. 使用 @Observed 注解

```java
@Service
public class UserService {

    @Observed(name = "user.find", contextualName = "findUserById")
    public User findById(Long id) {
        // 业务逻辑
    }
}
```

### 5. 手动创建 Observation

```java
@Service
public class OrderService {

    private final ObservationRegistry observationRegistry;

    public Order createOrder(CreateOrderRequest request) {
        return Observation.createNotStarted("order.create", observationRegistry)
            .lowCardinalityKeyValue("order.type", request.getType())
            .observe(() -> {
                // 业务逻辑
                return doCreateOrder(request);
            });
    }
}
```

## 配置参考

### 全局配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.enabled` | boolean | true | 全局开关 |
| `patra.observability.application-name` | String | null | 应用标识 |
| `patra.observability.environment` | String | dev | 环境标识（dev/staging/prod） |
| `patra.observability.region` | String | null | 区域标识 |
| `patra.observability.cluster` | String | default | 集群标识 |

### 指标配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.metrics.enabled` | boolean | true | 是否启用指标收集 |
| `patra.observability.metrics.prefix` | String | "" | 指标前缀 |
| `patra.observability.metrics.common-tags` | Map | {} | 公共标签 |

### Prometheus 配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.metrics.prometheus.enabled` | boolean | true | 是否启用 |
| `patra.observability.metrics.prometheus.enable-exemplars` | boolean | true | 是否启用 Exemplars（Metrics→Tracing 关联） |

### 日志配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.logging.enabled` | boolean | true | 是否启用日志集成 |
| `patra.observability.logging.include-trace-id` | boolean | true | 是否在日志中包含 trace_id |

### Tracing 配置（Agent JVM 参数）

| JVM 参数 | 说明 | 示例 |
|----------|------|------|
| `-Dotel.service.name` | 服务名称 | my-service |
| `-Dotel.traces.exporter` | 导出器类型 | otlp |
| `-Dotel.exporter.otlp.endpoint` | OTLP 端点 | http://otel-collector:4317 |
| `-Dotel.traces.sampler` | 采样器 | parentbased_traceidratio |
| `-Dotel.traces.sampler.arg` | 采样率 | 0.1 |

## 安全特性

### 高基数标签过滤

`HighCardinalityMeterFilter` 自动过滤以下高基数标签，防止时序数据库性能问题：

- userId、user_id
- requestId、request_id、traceId、trace_id、spanId、span_id
- sessionId、session_id
- correlationId、correlation_id
- transactionId、transaction_id

> **注意**：敏感数据脱敏由 OTel Agent 或日志框架配置处理，不在本模块范围内。

## 依赖关系

```
patra-spring-boot-starter-observability
├── patra-common-core                    # 公共工具和异常
├── patra-spring-boot-starter-core       # 核心 Starter（ResolutionInterceptor）
├── micrometer-observation               # Micrometer Observation API
├── micrometer-core                      # Micrometer 核心
├── micrometer-registry-prometheus       # (可选) Prometheus Registry
├── caffeine                             # 缓存（状态管理）
└── spring-web                           # (可选) REST Client
```

## 包结构

```
com.patra.starter.observability
├── autoconfigure/                       # 自动配置
│   ├── ObservabilityAutoConfiguration   # 核心自动配置
│   ├── MicrometerAutoConfiguration      # Micrometer 配置
│   ├── PrometheusAutoConfiguration      # Prometheus 配置
│   └── ObservationInterceptorsAutoConfiguration
├── config/
│   └── ObservabilityProperties          # 配置属性
├── filter/                              # MeterFilter
│   ├── CommonTagsMeterFilter            # 公共标签
│   ├── HighCardinalityMeterFilter       # 高基数标签过滤
│   └── MetricNamingMeterFilter          # 指标命名规范
└── interceptor/                         # 拦截器
    └── ObservationResolutionInterceptor # 错误解析拦截器
```

## 设计原则

1. **零配置启用**：默认启用所有功能，开箱即用
2. **Agent + SDK 分离**：Tracing 由 Agent 处理，Metrics 由 Micrometer 处理，职责清晰
3. **生产环境安全**：高基数过滤等安全特性自动启用
4. **内存安全**：使用 Caffeine Cache 管理状态，自动过期防止内存泄漏
5. **可扩展性**：支持自定义公共标签等
6. **与 Spring Boot 集成**：复用 Spring Boot Actuator 的自动配置，避免重复
