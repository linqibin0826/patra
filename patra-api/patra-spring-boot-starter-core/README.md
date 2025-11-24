# patra-spring-boot-starter-core

## 概述

核心基础设施 Starter,为所有 Patra 微服务提供统一的 JSON/XML 序列化、错误处理框架、可观测性和弹性支持的自动配置。遵循"约定优于配置"原则,开箱即用提供合理的默认值。

本 Starter 是所有业务模块的基石,提供统一的基础设施能力,确保跨服务的一致性和可观测性。

## 核心功能

- **JSON/XML 序列化**: 基于 Jackson 的标准化序列化配置(日期格式、空值处理等)
- **错误处理框架**: 可扩展的错误解析管道,通过 ResolutionInterceptor 扩展点支持自定义拦截器
- **统一时间源**: 提供全局 UTC Clock,支持可测试性和时间戳一致性
- **弹性支持**: 可选的 Resilience4j 熔断器,保护错误处理管道
- **扩展点机制**: 提供 ResolutionInterceptor 接口,允许外部模块（如 patra-spring-boot-starter-observability）注入可观测性功能

## 自动配置内容

### CoreAutoConfiguration
提供核心基础设施 Bean:
- `Clock`: 系统 UTC 时钟,用于统一的时间戳生成

### JacksonAutoConfiguration
配置 Spring 管理的 Jackson ObjectMapper:
- `ObjectMapperProvider`: 桥接 Spring 管理的 ObjectMapper 到非 Spring 代码路径

### XmlAutoConfiguration (可选)
当 `jackson-dataformat-xml` 在 classpath 中时自动激活:
- `XmlMapper`: 共享 Jackson 全局配置的 XML 映射器

### CoreErrorAutoConfiguration
统一错误处理管道,包含以下 Bean:
- `ErrorResolutionEngine`: 错误解析引擎,支持原因链遍历和特征映射
- `ErrorResolutionPipeline`: 拦截器责任链,执行错误处理流程
- `TraceProvider`: 追踪上下文提取器(默认基于 HTTP Header)
- `HttpStdErrors.Group`: 标准 HTTP 错误定义组

注意: 追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability，
通过 ResolutionInterceptor 扩展点机制提供。

### CircuitBreakerErrorAutoConfiguration (可选)
当 `resilience4j-circuitbreaker` 在 classpath 中时自动激活:
- `CircuitBreakerInterceptor`: 熔断器拦截器,保护错误处理管道

## 主要组件

### Clock (时间源)
统一的 UTC 时钟,便于测试和时间戳一致性:
```java
@Service
public class MyService {
    private final Clock clock;

    public void doSomething() {
        Instant now = Instant.now(clock);
    }
}
```

### ErrorResolutionPipeline (错误解析管道)
通过拦截器链解析异常,执行流程:
```
异常抛出
    ↓
ErrorResolutionPipeline
    ├─ CircuitBreakerInterceptor (熔断保护,可选)
    ├─ ... (自定义拦截器,如 observability starter 提供的追踪和指标拦截器)
    └─ 调用下游 ↓
    ↓
ErrorResolutionEngine
    ├─ ErrorMappingContributor (SPI: 自定义映射)
    ├─ 特征映射(基于异常特征)
    └─ 类名启发式(基于异常类名)
    ↓
ErrorResolution (标准化错误表示)
```

注意: 可观测性功能（追踪、指标）由 patra-spring-boot-starter-observability 通过 ResolutionInterceptor 扩展点提供。

## 扩展点

### 1. ErrorMappingContributor (SPI)
自定义异常到错误码的映射逻辑:
```java
@Component
public class MyErrorMappingContributor implements ErrorMappingContributor {
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        if (exception instanceof MyCustomException) {
            return Optional.of(new SimpleErrorCode(
                "CUSTOM_ERROR",
                "自定义错误",
                HttpStatus.BAD_REQUEST.value()
            ));
        }
        return Optional.empty();  // 传递给下一个贡献者
    }
}
```

### 2. ProblemFieldContributor (SPI)
向 ProblemDetail 添加自定义字段:
```java
@Component
public class MyProblemFieldContributor implements ProblemFieldContributor {
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception) {
        fields.put("service", "patra-ingest");
        fields.put("timestamp", Instant.now().toString());
    }
}
```

### 3. TraceProvider (SPI)
自定义追踪上下文提取策略:
```java
@Component
public class MyTraceProvider implements TraceProvider {
    @Override
    public Optional<String> getCurrentTraceId() {
        // 从 MDC、SkyWalking 或其他追踪系统中提取
        return Optional.ofNullable(TraceContext.traceId());
    }
}
```

### 4. ResolutionInterceptor (SPI)
添加自定义错误处理拦截器:
```java
@Component
@Order(100)
public class MyInterceptor implements ResolutionInterceptor {
    @Override
    public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
        // 前置处理
        log.info("处理异常: {}", exception.getMessage());

        // 调用链下游
        ErrorResolution resolution = invocation.proceed(exception);

        // 后置处理
        return resolution;
    }
}
```

## 配置属性

**配置前缀**: `patra.error`, `patra.tracing`

### 错误处理配置
```yaml
patra:
  error:
    enabled: true                           # 是否启用错误处理框架
    context-prefix: PATRA                   # 错误代码上下文前缀
    engine:
      max-cause-depth: 10                   # 原因链遍历最大深度
      enable-trait-mapping: true            # 是否启用特征映射
      enable-naming-heuristic: true         # 是否启用类名启发式
    observation:
      enabled: true                         # 是否启用错误观测
      slow-threshold-ms: 200                # 慢解析阈值(毫秒)
      log-slow-resolution: true             # 是否记录慢解析警告
    circuit-breaker:
      enabled: true                         # 是否启用熔断器
      failure-rate-threshold: 50.0          # 失败率阈值(百分比)
      minimum-number-of-calls: 20           # 最小调用次数
      sliding-window-size: 50               # 滑动窗口大小
      permitted-calls-in-half-open-state: 5 # 半开状态允许调用数
      wait-duration-in-open-state: 30s      # 断路器打开等待时长
```

### 追踪配置
```yaml
patra:
  tracing:
    header-names:
      - X-Trace-ID                          # 追踪 ID 的 HTTP 头名称列表
      - X-B3-TraceId
```

## 使用方式

### Maven 依赖
```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>
```

**传递依赖**(自动包含):
- `patra-common-core`: 领域基础类和工具
- `spring-boot-starter-json`: Jackson JSON 支持
- `resilience4j-circuitbreaker`: 熔断器(可选)
- `apm-toolkit-logback-1.x`, `apm-toolkit-trace`: SkyWalking APM 集成

**可选功能**:
- 可观测性（追踪、指标）: 添加 `patra-spring-boot-starter-observability` 依赖自动启用

### 配置示例

```yaml
spring:
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: true

patra:
  error:
    enabled: true
    context-prefix: INGEST
    circuit-breaker:
      enabled: true                        # 启用熔断器
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 20
```

### 代码示例

**使用统一时钟**:
```java
@Service
public class PlanIngestionOrchestrator {
    private final Clock clock;

    public void processPlan(PlanAggregate plan) {
        Instant ingestedAt = Instant.now(clock);
        plan.recordIngestion(ingestedAt);
    }
}
```

**使用错误解析管道**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ErrorResolutionPipeline pipeline;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex) {
        ErrorResolution resolution = pipeline.resolve(ex);
        ProblemDetail problem = ProblemDetail.forStatus(resolution.getHttpStatus());
        problem.setDetail(resolution.getMessage());
        return ResponseEntity.status(resolution.getHttpStatus()).body(problem);
    }
}
```

## 技术栈

- **Spring Boot**: 3.5.7
- **Jackson**: 2.18.2 (JSON/XML 序列化)
- **Resilience4j**: 2.2.0 (熔断器)
- **SkyWalking**: 9.4.0 (日志追踪集成)

---

**最后更新**: 2025-11-24 (重构: 移除 Micrometer 依赖，可观测性功能移至 patra-spring-boot-starter-observability)
**维护者**: Patra Team
