 # patra-spring-boot-starter-core

> 平台级 “错误解析 + 观测 + 熔断保护 + JSON 桥接” 基础 Starter。面向所有微服务统一接入。

## 1. 模块定位

本 Starter 为 Papertrace 平台各微服务提供**统一且可扩展**的错误解析与观测基座：

| 能力 | 说明 | 价值 |
|------|------|------|
| 错误解析引擎 | 将任意异常 → 结构化 `ErrorResolution(errorCode, httpStatus)` | 统一错误码、前端/调用方稳定处理 |
| 语义映射 (Trait) | 基于 `HasErrorTraits` / 命名启发式 | 降低硬编码 if/instanceof，支持语义演进 |
| SPI 扩展 | `ErrorMappingContributor` / `ProblemFieldContributor` / `TraceProvider` | 业务定制映射 / 增补字段 / 自定义链路来源 |
| 解析管线拦截器 | Tracing、Metrics、CircuitBreaker | 可插拔增强（指标、熔断、追踪） |
| 观测指标 | Micrometer 计数/耗时/慢调用/熔断 | 便于 APM / 报警 / SLO 衡量 |
| 熔断保护 | Resilience4j 包裹解析链 | 防御性：极端异常风暴时保护核心路径 |
| Jackson 桥接 | `ObjectMapperProvider` → `JsonMapperHolder` | 保证非 Spring 代码使用与容器一致配置 |

## 2. 快速开始

### 2.1 引入依赖
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2.2 基本配置
```yaml
patra:
    error:
        context-prefix: REG   # 必填：服务级错误码前缀（示例：REG / ING / GATE）
    tracing:
        header-names: [ traceId, X-B3-TraceId, traceparent, X-Trace-Id ]
```
> 未配置 `context-prefix` 时会回退为 `UNKNOWN-xxxx`（应在启动校验阶段尽早发现）。

### 2.3 典型用法（在 Web 层 / 自定义全局异常处理）
```java
@RestControllerAdvice
class GlobalHandler {
    private final ErrorResolutionPipeline pipeline;
    GlobalHandler(ErrorResolutionPipeline pipeline){this.pipeline=pipeline;}
    @ExceptionHandler(Throwable.class)
    ResponseEntity<ProblemDetail> handle(Throwable ex){
         var resolution = pipeline.resolve(ex);
         ProblemDetail pd = ProblemDetail.forStatus(resolution.httpStatus());
         pd.setDetail(ex.getMessage());
         pd.setProperty("code", resolution.errorCode().code());
         return ResponseEntity.status(resolution.httpStatus()).body(pd);
    }
}
```

## 3. 配置项（精简表）

### 3.1 `patra.error`
| 键 | 默认 | 说明 |
|----|------|------|
| enabled | true | 是否启用错误解析自动装配 |
| context-prefix | (无) | 统一错误码前缀（必填） |
| engine.max-cause-depth | 10 | 向下追溯 cause 链最大层级 |
| engine.enable-trait-mapping | true | 是否启用 Trait 解析 |
| engine.enable-naming-heuristic | true | 是否启用类名启发式 |
| observation.enabled | true | 是否启用观测/指标 |
| observation.slow-threshold-ms | 200 | 慢解析阈值 |
| observation.log-slow-resolution | true | 慢解析是否 WARN 日志 |
| circuit-breaker.enabled | true | 是否开启熔断保护 |
| circuit-breaker.failure-rate-threshold | 50 | 失败率阈值% |
| circuit-breaker.minimum-number-of-calls | 20 | 最小统计窗口调用数 |
| circuit-breaker.sliding-window-size | 50 | 窗口大小 |
| circuit-breaker.permitted-calls-in-half-open-state | 5 | 半开探测数 |
| circuit-breaker.wait-duration-in-open-state | 30s | 打开后等待时间 |

### 3.2 `patra.tracing`
| 键 | 默认 | 说明 |
|----|------|------|
| header-names | [traceId, X-B3-TraceId, traceparent] | 依次尝试获取的 MDC Key（通常由网关或链路过滤器写入） |

## 4. 解析引擎工作流

逻辑顺序（短路优先）：
1. ApplicationException → 直接取其 `ErrorCodeLike`
2. ErrorMappingContributor SPI（按 `@Order` 链式尝试）
3. HasErrorTraits（按 Trait → 标准 HTTP 段映射，如 NOT_FOUND→0404）
4. 命名启发式（类名后缀 *NotFound / *Conflict / *Validation / *Timeout ...）
5. cause 链回溯（深度 < maxCauseDepth）
6. Fallback：客户端可疑（Validation/Biding/Illegal…）→ 422；否则 500

返回统一 `ErrorResolution(errorCode, httpStatus)`；错误码格式 `<PREFIX>-04xx/05xx`。

## 5. 解析管线（Pipeline）机制

`ErrorResolutionPipeline` 将引擎包装为责任链：
```
TracingInterceptor (记录/绑定 traceId)
 → CircuitBreakerInterceptor (可选，防雪崩)
 → MetricsInterceptor (耗时指标 & 慢调用监测)
 → Engine.resolve()
```
拦截器可通过自定义 Bean 实现 `ResolutionInterceptor` 并加 `@Order` 插入。

## 6. SPI 扩展

| SPI | 触发点 | 典型用途 | 返回/效果 |
|-----|--------|----------|-----------|
| ErrorMappingContributor | Engine 第 2 步 | 精确为特定异常绑定业务码 | `Optional<ErrorCodeLike>` |
| ProblemFieldContributor | （与 web starter 合作）构建 ProblemDetail 时 | 自定义响应扩展字段 | 修改 `Map<String,Object>` |
| TraceProvider | TracingInterceptor | 自定义 traceId 来源（MDC / Reactor Context / ThreadLocal） | `Optional<String>` |

示例：
```java
@Component
@Order(10)
class DataErrorMapping implements ErrorMappingContributor {
    public Optional<ErrorCodeLike> mapException(Throwable ex){
         if(ex instanceof DuplicateKeyException dk){
                return Optional.of(MyErrors.DB_CONFLICT); // 实现 ErrorCodeLike
         }
         return Optional.empty();
    }
}
```

## 7. 对接其它 Starter

| Starter | 集成点 | 协同作用 |
|---------|--------|----------|
| patra-spring-boot-starter-web | 将 `ErrorResolution` → RFC7807 ProblemDetail | 统一前端错误结构 |
| patra-spring-cloud-starter-feign | 解码下游错误码并再封装 | 跨服务错误透传一致 |
| patra-spring-boot-starter-mybatis | DB 异常映射 Contributor | 结构化数据库错误 |
| patra-spring-boot-starter-rocketmq | 消费端错误分类/重试策略 | 基于错误码判定 DLQ / 重试 |

## 8. 指标 (Micrometer)

| 指标名 | 类型 | 标签 | 描述 |
|--------|------|------|------|
| papertrace.error.resolution.duration | Timer | context, exception, errorCode | 解析耗时 |
| papertrace.error.resolution.count | Counter | context, errorCode | 解析次数 |
| papertrace.error.resolution.slow | Counter | context, exception | 慢解析次数 |
| papertrace.error.resolution.circuit_breaker | Counter | context, exception | 熔断降级次数 |

Prometheus 示例抓取：
```promql
rate(papertrace_error_resolution_duration_seconds_count[5m])
```

## 9. 熔断策略说明

`CircuitBreakerInterceptor` 仅包裹解析流程（不影响业务主链路）：

- 打开后直接返回 `<PREFIX>-0503`；并记录 `circuit_breaker` 指标
- 适合防止意外扩张（如大量病态异常导致 Trait / Naming 解析耗时激增）

调优建议：
| 现象 | 调整 |
|------|------|
| 频繁误触发 | 提高 `failure-rate-threshold` 或扩大 `sliding-window-size` |
| 熔断恢复过慢 | 缩短 `wait-duration-in-open-state` |

## 10. 与 Json / ObjectMapper 的协同

`JacksonAutoConfiguration` + `ObjectMapperProvider`：

1. Spring 启动后将容器内 `ObjectMapper` 注册到 `JsonMapperHolder`
2. `patra-common` 中的 `JsonNormalizer` / 工具类即获取到同源配置
3. 非 Spring 代码（表达式引擎、静态工具）避免产生 “双配置” 差异

## 11. 最佳实践

| 场景 | 建议 |
|------|------|
| 领域层抛出错误 | 使用 `DomainException`（无 HTTP）→ 应用层包一层 `ApplicationException` 如需暴露错误码 |
| 业务可预期违例 | 直接抛 `ApplicationException` 携带自定义 `ErrorCodeLike` |
| 需要更细粒度分类 | 定义自有异常实现 `HasErrorTraits` 返回多个 Trait |
| 与下游集成 | 在 Feign / MQ 端实现 Contributor，将下游异常 → 平台码 |
| 前端统一处理 | 通过 web starter 统一生成 ProblemDetail（code / traceId / path / timestamp）|

## 12. 测试建议

```java
@SpringBootTest(properties = {
    "patra.error.context-prefix=TEST"
})
class ErrorEngineTest {
    @Autowired ErrorResolutionPipeline pipeline;
    @Test void appExceptionDirect(){
         var ex = new ApplicationException(TestErrors.INVALID, "bad");
         var r = pipeline.resolve(ex);
         assertThat(r.httpStatus()).isEqualTo(TestErrors.INVALID.httpStatus());
    }
}
```

## 13. FAQ

| 问题 | 解答 |
|------|------|
| 为什么需要命名启发式？ | 降低早期接入成本；未显式实现 Trait / Contributor 也能得到合理状态码 |
| 与 Spring `@ControllerAdvice` 冲突吗？ | 不冲突；本 Starter 只解析 → 你仍然控制响应结构 |
| 错误码缓存在哪里？ | 当前实现基于类名逻辑即时生成（轻量），可后续引入内存缓存提高超高频场景性能 |
| 何时禁用熔断？ | 解析路径性能稳定且 QPS 极低，可关闭以减少一次函数调用开销 |

## 14. Roadmap

| 项目 | 状态 | 说明 |
|------|------|------|
| ProblemDetail Web 封装（web starter） | 进行中 | 自动填充标准扩展字段 |
| ErrorResolution 二级缓存 | 规划 | 高频异常类常驻缓存，减少重复解析分支 |
| 动态 Trait → 状态映射配置 | 规划 | 允许通过配置中心调整某些 Trait 的状态码 |
| 全链路 Trace Provider SPI 扩展 | 规划 | 支持 SkyWalking / OpenTelemetry 上下文直接提取 |

## 15. 变更记录

- 0.1.0 初始：错误解析 + 观测 + 熔断 + TraceProvider + ObjectMapper 桥接
- 0.2.0 引入 Engine 配置分组、Observation / CircuitBreaker 配置项结构化

## 16. 附：快速代码片段
```java
// 1) 自定义错误码
enum MyErrors implements ErrorCodeLike { DATA_CONFLICT("REG-0409",409); /* ... */ }

// 2) 自定义 TraceProvider
@Component class ReactorTraceProvider implements TraceProvider {
    public Optional<String> getCurrentTraceId(){ return Optional.ofNullable(MDC.get("traceId")); }
}

// 3) 自定义指标阈值
# application.yaml
patra.error.observation.slow-threshold-ms: 100

// 4) 强制关闭命名启发式
patra.error.engine.enable-naming-heuristic: false
```

---
如需扩展/反馈请提交 Issue（标签：`starter:core`）。

> “Standardize failure first, optimize success later.”