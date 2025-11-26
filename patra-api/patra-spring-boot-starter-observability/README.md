# Patra Spring Boot Starter - Observability

可观测性 Starter，统一集成 Metrics、Tracing、Logging 三大支柱。

## 模块概述

本模块提供统一的可观测性基础设施，基于 Micrometer Observation API 构建，支持：

- **Metrics（指标）**：通过 Micrometer 收集和导出指标到 Prometheus、SkyWalking 等后端
- **Tracing（追踪）**：集成 SkyWalking APM，支持分布式追踪和 TraceID 传播
- **Logging（日志）**：日志与追踪关联，支持 TraceID 注入

## 核心功能

### 自动配置

| 配置类 | 功能 | 条件 |
|--------|------|------|
| `ObservabilityAutoConfiguration` | 核心配置，创建 ObservationRegistry，启用 @Observed 注解 | 默认启用 |
| `MicrometerAutoConfiguration` | 注册 Filter、Handler、MeterFilter | metrics.enabled=true |
| `PrometheusAutoConfiguration` | 配置 Prometheus Registry 和 Exemplars | prometheus.enabled=true |
| `SkyWalkingMeterAutoConfiguration` | 创建 SkyWalking Meter Registry | skywalking.enabled=true |
| `ObservationInterceptorsAutoConfiguration` | 注册拦截器（错误解析、HTTP 客户端、分布式锁） | 默认启用 |

### ObservationHandler

| Handler | 功能 | 配置项 |
|---------|------|--------|
| `LoggingObservationHandler` | 记录 Observation 生命周期到日志 | handlers.logging.enabled |
| `PerformanceObservationHandler` | 检测慢操作，记录执行时间 | handlers.performance.enabled |

### ObservationFilter

| Filter | 功能 | 执行顺序 |
|--------|------|----------|
| `SensitiveDataObservationFilter` | 检测并告警敏感数据（生产环境强制启用） | HIGHEST_PRECEDENCE |
| `CommonTagsObservationFilter` | 添加公共标签（application、environment 等） | LOWEST_PRECEDENCE |

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
| `RestClientObservationInterceptor` | 为 HTTP 请求创建 Observation | REST 客户端 |
| `LockMetricsRecorder` | 记录分布式锁指标（等待时间、持有时间、成功/失败率） | Redisson 分布式锁 |

> **注意**：批处理可观测性已迁移至 `patra-spring-boot-starter-batch`，使用 Spring Batch 原生 `BatchObservabilityBeanPostProcessor` 实现零配置集成。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-observability</artifactId>
</dependency>
```

### 2. 配置属性

```yaml
patra:
  observability:
    enabled: true
    application-name: ${spring.application.name}
    environment: dev
    region: cn-east-1
    cluster: default

    metrics:
      enabled: true
      prefix: ""
      step: 60s
      common-tags:
        team: platform
        version: ${project.version}

      prometheus:
        enabled: true
        enable-exemplars: true

      skywalking:
        enabled: true
        oap-address: skywalking-oap:11800

    tracing:
      enabled: true
      sampling-rate: 1.0
      baggage-fields:
        - X-Request-Id
        - X-Correlation-Id

    logging:
      enabled: true
      include-trace-id: true

    handlers:
      logging:
        enabled: true
        log-level: DEBUG
      performance:
        enabled: true
        slow-threshold: 3s

    security:
      mask-sensitive-data: true
      sensitive-patterns:
        - "custom-secret-\\d+"
      masking-disabled-in-environments:
        - dev-local
```

### 3. 使用 @Observed 注解

```java
@Service
public class UserService {

    @Observed(name = "user.find", contextualName = "findUserById")
    public User findById(Long id) {
        // 业务逻辑
    }
}
```

### 4. 手动创建 Observation

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
| `patra.observability.metrics.step` | Duration | 60s | 导出间隔 |
| `patra.observability.metrics.common-tags` | Map | {} | 公共标签 |

### Prometheus 配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.metrics.prometheus.enabled` | boolean | true | 是否启用 |
| `patra.observability.metrics.prometheus.enable-exemplars` | boolean | true | 是否启用 Exemplars |

### SkyWalking 配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.metrics.skywalking.enabled` | boolean | true | 是否启用 |
| `patra.observability.metrics.skywalking.oap-address` | String | skywalking-oap:11800 | OAP 服务器地址 |

### 追踪配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.tracing.enabled` | boolean | true | 是否启用追踪 |
| `patra.observability.tracing.sampling-rate` | double | 1.0 | 采样率（0.0-1.0） |
| `patra.observability.tracing.baggage-fields` | List | [X-Request-Id, X-Correlation-Id] | Baggage 传播字段 |

### Handler 配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.handlers.logging.enabled` | boolean | true | 是否启用日志 Handler |
| `patra.observability.handlers.logging.log-level` | String | DEBUG | 日志级别 |
| `patra.observability.handlers.performance.enabled` | boolean | true | 是否启用性能 Handler |
| `patra.observability.handlers.performance.slow-threshold` | Duration | 3s | 慢操作阈值 |

### 安全配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.security.mask-sensitive-data` | boolean | true | 是否启用敏感数据脱敏 |
| `patra.observability.security.sensitive-patterns` | List | [] | 自定义敏感数据模式（正则） |
| `patra.observability.security.masking-disabled-in-environments` | List | [dev-local] | 允许禁用脱敏的环境 |

## 安全特性

### 敏感数据检测

`SensitiveDataObservationFilter` 自动检测以下敏感数据：

**敏感字段名**：password、pwd、token、secret、api-key、auth、credential

**敏感数据值**（正则匹配）：
- 身份证号（15/18 位）
- 手机号
- 固定电话
- 邮箱地址
- 银行卡号
- Bearer Token
- Basic Auth

**生产环境强制启用**：当 `environment=prod` 时，敏感数据脱敏自动强制启用。

### 高基数标签过滤

`HighCardinalityMeterFilter` 自动过滤以下高基数标签，防止时序数据库性能问题：

- userId、user_id
- requestId、request_id、traceId、trace_id、spanId、span_id
- sessionId、session_id
- correlationId、correlation_id
- transactionId、transaction_id

## 依赖关系

```
patra-spring-boot-starter-observability
├── patra-common-core                    # 公共工具和异常
├── patra-spring-boot-starter-core       # 核心 Starter（ResolutionInterceptor）
├── patra-spring-boot-starter-redisson   # (可选) Redisson 分布式锁（LockObserver SPI）
├── micrometer-observation               # Micrometer Observation API
├── micrometer-core                      # Micrometer 核心
├── micrometer-registry-prometheus       # (可选) Prometheus Registry
├── apm-toolkit-micrometer-registry      # (可选) SkyWalking Meter Registry
├── apm-toolkit-trace                    # SkyWalking Trace API
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
│   ├── SkyWalkingMeterAutoConfiguration # SkyWalking 配置
│   └── ObservationInterceptorsAutoConfiguration
├── config/
│   └── ObservabilityProperties          # 配置属性
├── filter/                              # 过滤器
│   ├── CommonTagsMeterFilter            # 公共标签（Meter）
│   ├── CommonTagsObservationFilter      # 公共标签（Observation）
│   ├── HighCardinalityMeterFilter       # 高基数标签过滤
│   ├── MetricNamingMeterFilter          # 指标命名规范
│   └── SensitiveDataObservationFilter   # 敏感数据检测
├── handler/                             # 处理器
│   ├── LoggingObservationHandler        # 日志 Handler
│   └── PerformanceObservationHandler    # 性能 Handler
├── interceptor/                         # 拦截器
│   ├── ObservationResolutionInterceptor # 错误解析拦截器
│   ├── RestClientObservationInterceptor # HTTP 客户端拦截器
│   └── redisson/
│       └── LockMetricsRecorder          # 分布式锁指标记录器（实现 LockObserver SPI）
```

## 设计原则

1. **零配置启用**：默认启用所有功能，开箱即用
2. **生产环境安全**：敏感数据检测、高基数过滤等安全特性自动启用
3. **内存安全**：使用 Caffeine Cache 管理状态，自动过期防止内存泄漏
4. **可扩展性**：支持自定义敏感数据模式、公共标签、Handler 等
5. **与 Spring Boot 集成**：复用 Spring Boot Actuator 的自动配置，避免重复
