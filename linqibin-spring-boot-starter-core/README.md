# patra-spring-boot-starter-core

## 概述

核心基础设施 Starter,为所有 Patra 微服务提供统一的 JSON/XML 序列化、错误处理框架、可观测性和弹性支持的自动配置。遵循"约定优于配置"原则,开箱即用提供合理的默认值。

本 Starter 是所有业务模块的基石,提供统一的基础设施能力,确保跨服务的一致性和可观测性。

## 核心功能

- **CommandBus 命令总线**: CQRS 命令分发基础设施,自动发现 Handler,支持拦截器链
- **JSON/XML 序列化**: 基于 Jackson 的标准化序列化配置(日期格式、空值处理等)
- **错误处理框架**: 可扩展的错误解析管道,通过 ResolutionInterceptor 扩展点支持自定义拦截器
- **统一时间源**: 提供全局 UTC Clock,支持可测试性和时间戳一致性
- **异步线程池管理**: 命名线程池注册表,支持配置驱动创建和 Micrometer 指标集成
- **弹性支持**: 可选的 Resilience4j 熔断器,保护错误处理管道
- **扩展点机制**: 提供 ResolutionInterceptor 接口,允许外部模块（如 patra-spring-boot-starter-observability）注入可观测性功能

## 自动配置内容

### CommandBusAutoConfiguration
CQRS 命令总线自动配置:
- `CommandBus`: 命令分发接口（来自 patra-common-core）
- `SimpleCommandBus`: 默认实现,启动时自动发现并注册所有 CommandHandler
- 内置拦截器（条件装配）:
  - `TracingCommandInterceptor` (Order=50): 基于 Micrometer Observation API
  - `LoggingCommandInterceptor` (Order=100): 执行日志和耗时记录
  - `MetricsCommandInterceptor` (Order=200): Micrometer 指标收集
- 可配置的异步执行线程池

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

### AsyncAutoConfiguration
异步线程池自动配置,提供:
- `AsyncExecutorRegistry`: 命名线程池注册表,管理多个独立的线程池
- 根据 `patra.async.pools.*` 配置自动创建线程池
- 可选的 Micrometer 指标集成(当 MeterRegistry 可用时自动注册)

## 主要组件

### CommandBus (命令总线)
CQRS 写操作入口,Adapter 层统一通过 CommandBus 调用业务逻辑:

**核心特性**:
- **类型安全**: 通过泛型自动路由到正确的 Handler
- **拦截器链**: 支持 Logging/Metrics/Tracing 横切关注点
- **异步执行**: `handleAsync()` 返回 CompletableFuture
- **自动发现**: 启动时扫描并注册所有 `CommandHandler` 实现

**Adapter 层使用**:
```java
@RestController
@RequiredArgsConstructor
public class TaskController {
    private final CommandBus commandBus;

    @PostMapping("/tasks")
    public TaskResponse createTask(@RequestBody TaskRequest request) {
        // 构造 Command 并通过 CommandBus 分发
        CreateTaskCommand command = new CreateTaskCommand(request.name());
        TaskResult result = commandBus.handle(command);
        return TaskResponse.from(result);
    }

    @PostMapping("/tasks/async")
    public CompletableFuture<TaskResponse> createTaskAsync(@RequestBody TaskRequest request) {
        // 异步执行
        CreateTaskCommand command = new CreateTaskCommand(request.name());
        return commandBus.handleAsync(command)
            .thenApply(TaskResponse::from);
    }
}
```

**实现 CommandHandler**:
```java
@Component
public class CreateTaskHandler implements CommandHandler<CreateTaskCommand, TaskResult> {

    private final TaskRepository taskRepository;
    private final Clock clock;

    @Override
    @Transactional
    public TaskResult handle(CreateTaskCommand command) {
        Task task = Task.create(command.name(), Instant.now(clock));
        taskRepository.save(task);
        return new TaskResult(task.getId());
    }
}
```

**定义 Command**:
```java
// Command 必须实现 Command<R> 接口,R 为返回类型
public record CreateTaskCommand(String name) implements Command<TaskResult> {}
```

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

### AsyncExecutorRegistry (异步线程池注册表)
管理命名线程池,支持按业务场景创建独立的线程池:

**核心功能**:
- **命名管理**: 按名称注册和获取线程池,支持多个独立线程池
- **配置驱动**: 通过 `patra.async.pools.*` 配置动态创建
- **指标集成**: 自动注册 Micrometer 线程池指标(当 MeterRegistry 可用)
- **优雅关闭**: 实现 `DisposableBean`,应用关闭时正确终止所有线程池

**使用方式**:
```java
@Service
public class MyService {
    private final AsyncExecutorRegistry asyncExecutorRegistry;

    public void doAsync() {
        // 获取命名线程池并提交任务
        CompletableFuture.runAsync(
            () -> { /* 异步任务 */ },
            asyncExecutorRegistry.getExecutor("cache-upload")
        );
    }

    public void checkPool() {
        // 检查线程池是否存在
        if (asyncExecutorRegistry.hasExecutor("cache-upload")) {
            // 执行任务
        }
    }
}
```

**Micrometer 指标**(当 MeterRegistry 可用时自动注册):
- `executor.pool.size` - 当前池大小
- `executor.pool.core` - 核心线程数
- `executor.pool.max` - 最大线程数
- `executor.active` - 活跃线程数
- `executor.queued` - 队列中等待的任务数
- `executor.completed` - 已完成任务数

标签: `name={poolName}`

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
        // 从 MDC 或 OpenTelemetry 追踪系统中提取
        return Optional.ofNullable(MDC.get("traceId"));
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

**配置前缀**: `patra.command-bus`, `patra.error`, `patra.tracing`, `patra.async`

### CommandBus 配置
```yaml
patra:
  command-bus:
    async:
      core-pool-size: 4                # 核心线程数(默认 4)
      max-pool-size: 16                # 最大线程数(默认 16)
      queue-capacity: 100              # 队列容量(默认 100)
      thread-name-prefix: cmd-bus-     # 线程名前缀(默认 cmd-bus-)
    interceptors:
      logging: true                    # 启用日志拦截器(默认 true)
      metrics: true                    # 启用指标拦截器(默认 true)
      tracing: true                    # 启用追踪拦截器(默认 true)
```

**配置属性说明**:
| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.command-bus.async.core-pool-size` | int | 4 | 异步执行核心线程数 |
| `patra.command-bus.async.max-pool-size` | int | 16 | 异步执行最大线程数 |
| `patra.command-bus.async.queue-capacity` | int | 100 | 异步任务队列容量 |
| `patra.command-bus.async.thread-name-prefix` | String | `cmd-bus-` | 线程名前缀 |
| `patra.command-bus.interceptors.logging` | boolean | true | 启用执行日志和耗时记录 |
| `patra.command-bus.interceptors.metrics` | boolean | true | 启用 Micrometer 指标收集 |
| `patra.command-bus.interceptors.tracing` | boolean | true | 启用 Observation API 追踪 |

### 异步线程池配置
```yaml
patra:
  async:
    enabled: true                              # 是否启用异步线程池管理(默认 true)
    pools:                                     # 命名线程池配置
      cache-upload:                            # 线程池名称(自定义)
        core-size: 2                           # 核心线程数(默认 2)
        max-size: 4                            # 最大线程数(默认 4)
        queue-capacity: 100                    # 队列容量(默认 100)
        keep-alive-seconds: 60                 # 空闲线程存活时间(秒,默认 60)
        thread-name-prefix: cache-upload-      # 线程名前缀(默认 async-{name}-)
      data-sync:                               # 另一个线程池
        core-size: 4
        max-size: 8
        queue-capacity: 200
        thread-name-prefix: data-sync-
```

**配置属性说明**:
| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.async.enabled` | boolean | `true` | 是否启用异步线程池管理 |
| `patra.async.pools.{name}.core-size` | int | 2 | 核心线程数 |
| `patra.async.pools.{name}.max-size` | int | 4 | 最大线程数 |
| `patra.async.pools.{name}.queue-capacity` | int | 100 | 任务队列容量 |
| `patra.async.pools.{name}.keep-alive-seconds` | int | 60 | 空闲线程存活时间(秒) |
| `patra.async.pools.{name}.thread-name-prefix` | String | `async-{name}-` | 线程名前缀 |

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

### Gradle 依赖

```kotlin
implementation(project(":patra-spring-boot-starter-core"))
```

**传递依赖**(自动包含):
- `patra-common-core`: 领域基础类和工具,包含 `Command`/`CommandBus`/`CommandHandler` 接口
- `spring-boot-starter-json`: Jackson JSON 支持
- `resilience4j-circuitbreaker`: 熔断器(可选)

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
public class PlanIngestionHandler {
    private final Clock clock;

    public void handle(PlanIngestionCommand command) {
        Instant ingestedAt = Instant.now(clock);
        // 处理命令...
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

- **Spring Boot**: 4.0.1
- **Jackson**: 3.0.3 (JSON/XML 序列化)
- **Resilience4j**: 2.2.0 (熔断器)

---

**最后更新**: 2026-01-14 (迁移至 Gradle 构建)
**维护者**: Patra Team
