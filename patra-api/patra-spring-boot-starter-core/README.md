# patra-spring-boot-starter-core

平台级错误解析与观测基础 Starter，为所有微服务提供统一错误码、Trace 与熔断能力。

## 1. 模块定位
- **服务/组件作用**：将异常解析为结构化 `ErrorResolution`，并通过拦截器注入 Trace、指标、熔断保护
- **主要消费者**：所有 Spring Boot 服务（adapter 层）、web/feign/mybatis 等扩展 Starter
- **架构边界**：专注横切能力；业务定制通过 SPI 扩展，不应在 Starter 中编写具体业务逻辑

## 2. 核心能力
- **错误解析引擎**：ApplicationException → ErrorMappingContributor → Trait → 命名启发式 → 兜底
- **解析管线**：Tracing、CircuitBreaker、Metrics 拦截器，可按 `@Order` 扩展
- **SPI 扩展**：`ErrorMappingContributor`、`ProblemFieldContributor`、`TraceProvider`、`ResolutionInterceptor`
- **观测指标**：Micrometer 计数/耗时/慢解析/熔断指标
- **ObjectMapper 桥接**：统一 `JsonMapperHolder` 与 Spring `ObjectMapper`

> 本页包含配置表、示例代码与 FAQ；如需对比其它 Starter，请参考各 Starter 模块 README（`patra-spring-boot-starter-*`、`patra-spring-cloud-starter-feign`）。

## 3. 分层结构与依赖
- 主要包：`error`（解析引擎）、`pipeline`（拦截器）、`spi`（扩展接口）、`tracing`、`observation`
- 依赖：`patra-common`、Spring Boot、Micrometer、Resilience4j、Jackson
- 禁止事项：在 Starter 中硬编码业务错误码或下游服务逻辑

## 4. 运行与配置
- Maven 引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 必要配置：
  ```yaml
  patra:
    error:
      context-prefix: REG   # 必填：服务级错误码前缀（REG/ING/GATE 等）
    tracing:
      header-names: [traceId, X-B3-TraceId, traceparent]
  ```
- 典型用法：在 `@RestControllerAdvice` 中注入 `ErrorResolutionPipeline` 并将结果组装为 ProblemDetail

## 5. 观测与运维
- 指标：`papertrace.error.resolution.duration/count/slow/circuit_breaker`
- 慢解析监控：通过 `patra.error.observation.slow-threshold-ms` 调整阈值，并可启用 WARN 日志
- 熔断保护：避免异常风暴；关注 `circuit_breaker` 指标并调整 `failure-rate-threshold`

## 6. 测试策略
- Spring Boot Test 注入 `ErrorResolutionPipeline`，验证 ApplicationException/Trait/命名启发式路径
- 模拟 Contributor 覆盖率：自定义异常映射、TraceProvider、拦截器
- 性能基线：对高频异常执行基准，必要时缓存解析结果

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| Web ProblemDetail 集成 | 进行中 | 确保字段一致性并与前端协议同步 |
| ErrorResolution 缓存 | 规划 | 注意内存占用与过期策略 |
| Trait → 状态动态配置 | 规划 | 需要配置中心支持与灰度验证 |
| TraceProvider 扩展 | 规划 | 支持 SkyWalking/OpenTelemetry 上下文提取 |

主要风险：context-prefix 漏配导致 UNKNOWN 错误码、过度熔断影响解析、扩展 SPI 未注册顺序导致优先级异常。

## 8. 参考资料
- 其他 Starter：`patra-spring-boot-starter-web/README.md`、`patra-spring-cloud-starter-feign/README.md`、`patra-spring-boot-starter-mybatis/README.md`、`patra-spring-boot-starter-expr/README.md`
- Web 层落地：`patra-spring-boot-starter-web/README.md`
- Feign 错误处理：`patra-spring-cloud-starter-feign/README.md`
- 错误规范：`docs/standards/platform-error-handling.md`
