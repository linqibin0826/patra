# patra-spring-boot-starter-core

> **核心 Spring Boot Starter**,为所有 Papertrace 微服务提供 JSON 序列化、错误处理、可观测性和弹性支持的基础自动配置。

---

## 📌 目的

本 Starter 为所有 Papertrace 服务提供**必需的基础设施**:

1. **Jackson 配置**: 标准化的 JSON 序列化(日期格式、null 处理等)
2. **错误处理框架**: 包含追踪和指标的复杂错误解析管道
3. **可观测性**: 与 Micrometer(指标)和 SkyWalking(追踪)集成
4. **弹性支持**: 通过 Resilience4j 实现熔断器集成
5. **日志记录**: 预配置的 Logback,支持追踪上下文传播

**核心原则**: 约定优于配置——服务开箱即用地获得合理的默认值。

---

## 🔧 自动配置

### 1. JacksonAutoConfiguration

**目的**: 在所有服务间标准化 JSON 序列化。

**功能**:
- 使用 Papertrace 约定配置 `ObjectMapper`:
  - ISO-8601 日期/时间格式化
  - 仅包含非空字段
  - 遇到未知属性时失败(严格模式)
  - 注册 Java 8 时间模块
- 提供 `@JsonComponent` 支持
- 启用 `JsonNode` 支持动态 JSON 字段

**配置属性**:
```yaml
spring:
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: true
```

---

### 2. XmlAutoConfiguration

**目的**: 为处理 XML 的模块(如 PubMed 负载)提供项目级 `XmlMapper`。

**功能**:
- 暴露由 Spring Boot 的 `Jackson2ObjectMapperBuilder` 构建的单例 `XmlMapper`
- 仅在 classpath 中存在 `jackson-dataformat-xml` 时激活
- 与 JSON 共享相同的全局 Jackson 模块和设置

无需额外配置;需要时直接注入 `XmlMapper` 即可。

---

### 3. CoreErrorAutoConfiguration

**目的**: 统一错误处理,支持可扩展的解析管道。

**架构**:
```
异常抛出
    ↓
ErrorResolutionEngine
    ↓
ResolutionPipeline (拦截器链)
    ├─ TracingInterceptor (传播追踪上下文)
    ├─ MetricsInterceptor (记录错误指标)
    ├─ CircuitBreakerInterceptor (触发熔断器)
    └─ ... (自定义拦截器)
    ↓
ErrorMappingContributor (SPI: 映射异常 → HTTP 状态码)
    ↓
ProblemDetail (RFC 7807 格式)
```

**核心组件**:

| 组件 | 目的 |
|-----------|---------|
| **ErrorResolutionEngine** | 通过管道编排错误解析 |
| **ResolutionPipeline** | 拦截器的责任链 |
| **ErrorMappingContributor** | 自定义异常 → HTTP 映射的 SPI |
| **ProblemFieldContributor** | 向 problem details 添加自定义字段的 SPI |
| **TraceProvider** | 追踪上下文提取的 SPI |

**SPI 扩展点**:

```java
// 自定义错误映射
@Component
public class MyErrorMappingContributor implements ErrorMappingContributor {
    @Override
    public ProblemDetail map(Exception ex) {
        if (ex instanceof MyCustomException) {
            return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
            );
        }
        return null;  // 让下一个贡献者处理
    }
}

// 向 problem details 添加自定义字段
@Component
public class MyProblemFieldContributor implements ProblemFieldContributor {
    @Override
    public void contribute(ProblemDetail problem, Exception ex) {
        problem.setProperty("correlationId", MDC.get("correlationId"));
        problem.setProperty("service", "patra-ingest");
    }
}
```

---

## 📊 可观测性

### 指标 (Micrometer)

**自动配置的指标**:
- 按异常类型统计的错误计数
- 错误解析持续时间
- 熔断器状态变化

**访问方式**:
```java
@Autowired
private MeterRegistry meterRegistry;

// 记录自定义指标
meterRegistry.counter("plan.ingestion.success").increment();
```

### 追踪 (SkyWalking)

**特性**:
- 日志中自动传播追踪上下文
- 将追踪 ID 注入错误响应
- 与 SkyWalking agent 集成

**日志格式**(包含追踪信息):
```
[2025-01-12 10:30:45] [TID:abc123] [INFO] [PlanIngestionOrchestrator] Plan ingestion success, planId=123
```

---

## 🛡️ 弹性支持

### 熔断器集成

为错误处理自动配置的 **Resilience4j**:

```java
@CircuitBreaker(name = "registry-service", fallbackMethod = "registryFallback")
public ProvenanceConfig fetchConfig(ProvenanceCode code) {
    return registryClient.getConfig(code);
}

public ProvenanceConfig registryFallback(ProvenanceCode code, Exception ex) {
    // 降级逻辑
    return getCachedConfig(code);
}
```

**熔断器事件**自动记录日志并收集指标。

---

## 🔗 依赖

本 Starter 被所有微服务 `-boot` 模块包含:

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>
```

**传递依赖**(自动包含):
- `patra-common` (领域基础类)
- Spring Boot Autoconfigure
- Spring Boot Starter JSON (Jackson)
- Micrometer Core
- Resilience4j CircuitBreaker
- SkyWalking Logback toolkit

---

## 🚀 用法

### 在应用层

**错误处理**:
```java
@Service
public class PlanIngestionOrchestrator {

    public PlanIngestionResult ingest(PlanIngestionCommand cmd) {
        try {
            // 业务逻辑
            return result;
        } catch (DomainException ex) {
            // 领域异常自动映射为应用层异常
            throw new PlanAssemblyException("Plan assembly failed", ex);
        }
    }
}
```

### 在适配器层

**错误映射**:
```java
@RestControllerAdvice
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

    @Override
    public ProblemDetail map(Exception ex) {
        if (ex instanceof ProvenanceNotFoundException) {
            return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Provenance not found: " + ex.getMessage()
            );
        }
        return null;
    }
}
```

**自动功能**:
- 追踪 ID 注入到错误响应
- 为所有错误记录指标
- 在重复失败时触发熔断器

---

## ⚙️ 配置

### application.yml

```yaml
# 错误处理
patra:
  error:
    include-trace: true              # 在错误中包含追踪 ID
    include-stack-trace: false       # 在生产环境隐藏堆栈跟踪
    max-resolution-time: 5000        # 错误解析最大时间(毫秒)

# 追踪
patra:
  tracing:
    enabled: true
    trace-header: X-Trace-ID         # 追踪 ID 的 HTTP 头
    span-header: X-Span-ID

# 熔断器
resilience4j:
  circuitbreaker:
    instances:
      registry-service:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

---

## 🧪 测试

### 单元测试

```java
@SpringBootTest
class ErrorResolutionEngineTest {

    @Autowired
    private ErrorResolutionEngine engine;

    @Test
    void shouldMapDomainExceptionToHttp404() {
        // Given
        Exception ex = new ProvenanceNotFoundException("Not found");

        // When
        ErrorResolution resolution = engine.resolve(ex);

        // Then
        assertEquals(404, resolution.httpStatus());
        assertEquals("Provenance not found", resolution.message());
    }
}
```

---

## 📈 性能

**错误解析开销**: 每次错误 < 1ms (使用 Micrometer 测量)

**熔断器**: 达到阈值后快速失败(防止级联故障)

**日志记录**: 与 SkyWalking 的异步日志(非阻塞)

---

## 🔗 相关文档

- [主 README](../README.md)
- [架构指南](../docs/ARCHITECTURE.md)
- [patra-common README](../patra-common/README.md) — 错误基础类

---

**最后更新**: 2025-01-12
