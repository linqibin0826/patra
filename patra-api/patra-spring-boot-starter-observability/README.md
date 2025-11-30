# Patra Spring Boot Starter - Observability

可观测性 Starter，统一集成 Metrics、Tracing、Logging 三大支柱。

## 架构说明

本模块采用 **OTel Agent + Micrometer Bridge** 架构：

| 支柱 | 技术方案 | 说明 |
|------|----------|------|
| **Tracing** | OTel Java Agent | 通过 `-javaagent` 参数启动，零代码侵入，自动 instrument HTTP/JDBC/Redis/MQ |
| **Metrics** | OTel Agent + Micrometer Bridge | Micrometer 指标通过 Agent 桥接，统一走 OTLP 导出到 OTel Collector |
| **Logging** | Agent MDC 注入 | Agent 自动将 `trace_id`/`span_id` 注入日志上下文 |

> **为什么选择 Agent 模式？**
> - 零代码侵入：无需修改应用代码即可获得完整的分布式追踪
> - 自动覆盖：HTTP、JDBC、Redis、消息队列等基础设施自动 instrument
> - 统一遥测管道：Traces/Metrics/Logs 全部通过 OTLP 导出，架构简洁

## 核心功能

### 自动配置

| 配置类 | 功能 | 条件 |
|--------|------|------|
| `ObservabilityAutoConfiguration` | 核心配置，创建 ObservationRegistry，启用 @Observed 注解 | 默认启用 |
| `MicrometerAutoConfiguration` | 桥接 OTel Agent MeterRegistry，注册 MeterFilter | metrics.enabled=true |
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
     -Dotel.javaagent.configuration-file=/path/to/otel-dev.properties \
     -Dotel.service.name=my-service \
     -jar my-app.jar
```

关键 Agent 配置（`otel-dev.properties`）：

```properties
# 统一 OTLP 导出
otel.exporter.otlp.endpoint=http://localhost:4317
otel.exporter.otlp.protocol=grpc

# 启用 Metrics 和 Micrometer Bridge
otel.metrics.exporter=otlp
otel.instrumentation.micrometer.enabled=true
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
| `patra.observability.application-name` | String | `${spring.application.name}` | 应用标识（自动从 Spring 获取） |
| `patra.observability.environment` | String | `${spring.profiles.active}` | 环境标识（自动从 profile 获取，"default" 映射为 "dev"） |
| `patra.observability.region` | String | null | 区域标识 |
| `patra.observability.cluster` | String | default | 集群标识 |

### 指标配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.observability.metrics.enabled` | boolean | true | 是否启用指标收集 |
| `patra.observability.metrics.prefix` | String | "" | 指标前缀（所有指标自动添加 `patra.` 前缀） |
| `patra.observability.metrics.common-tags` | Map | {} | 公共标签 |

### Tracing 配置（Agent JVM 参数）

| JVM 参数 | 说明 | 示例 |
|----------|------|------|
| `-Dotel.service.name` | 服务名称 | my-service |
| `-Dotel.exporter.otlp.endpoint` | OTLP 端点 | http://otel-collector:4317 |
| `-Dotel.traces.sampler` | 采样器 | parentbased_traceidratio |
| `-Dotel.traces.sampler.arg` | 采样率 | 1.0 |
| `-Dotel.instrumentation.micrometer.enabled` | 启用 Micrometer Bridge | true |

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
├── caffeine                             # 缓存（状态管理）
└── spring-boot-starter-actuator         # Actuator（健康检查等）
```

## 包结构

```
com.patra.starter.observability
├── autoconfigure/                       # 自动配置
│   ├── ObservabilityAutoConfiguration   # 核心自动配置
│   ├── MicrometerAutoConfiguration      # Micrometer 配置 + OTel Bridge
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
2. **统一遥测管道**：Traces/Metrics/Logs 全部通过 OTel Agent + OTLP 导出
3. **生产环境安全**：高基数过滤等安全特性自动启用
4. **内存安全**：使用 Caffeine Cache 管理状态，自动过期防止内存泄漏
5. **可扩展性**：支持自定义公共标签等
6. **与 Spring Boot 集成**：复用 Spring Boot Actuator 的自动配置，避免重复
