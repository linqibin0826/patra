# 平台级错误处理规范

本文定义 Papertrace 平台统一的错误处理体系，指导核心模块（core）、Web 适配层（web）与远程调用适配层（feign）的设计、配置与扩展方式，确保所有微服务输出一致、可观测、可治理的错误响应。

## 1. 总体目标

- **统一语义**：所有错误必须映射到平台错误码（`<上下文前缀>-<HTTP 后缀>`），并产出 RFC 7807 ProblemDetail 结构。
- **可观测性**：核心解析与各适配层通过 Micrometer 暴露指标，可按需降级为 NoOp。
- **可扩展性**：通过 SPI / 拦截器组合实现解耦；业务可在不破坏主干的前提下定制策略。
- **链路关联**：TraceId 自主注入与透传，保证跨服务诊断能力。
## 2. 核心模块（patra-spring-boot-starter-core）

### 2.1 关键组件
- `ErrorResolutionEngine`：纯解析内核，默认实现 `DefaultErrorResolutionEngine` 负责按照 ApplicationException → ErrorMappingContributor → Trait → 命名启发式 → 兜底 的顺序解析异常。
- `ErrorResolutionPipeline`：拦截器管线，以责任链形式组合追踪、熔断、指标等横切能力。
- `ResolutionInterceptor`：核心拦截 SPI，当前提供 `TracingInterceptor`、`CircuitBreakerInterceptor`、`MetricsInterceptor`。
- `ErrorObservationRecorder`：观测抽象，默认 `MicrometerErrorObservationRecorder`，无 MeterRegistry 时回退 `NO_OP`。
- 配置集中于 `ErrorProperties`（引擎行为、观测、熔断）与 `TracingProperties`（TraceId 读取头）。

### 2.2 扩展规则
- 业务若需自定义错误码映射，应实现 `ErrorMappingContributor` 并注册为 Bean。
- 自定义状态映射：实现 `StatusMappingStrategy`；如需特殊 Trace 获取，提供 `TraceProvider`。
- 观测：若接入第三方指标体系，可自定义 `ErrorObservationRecorder` 替换默认实现。
- 遵循六边形：核心模块不引入 Web/Feign 依赖，保持纯粹。
## 3. Web 适配层（patra-spring-boot-starter-web）

### 3.1 组件职责
- `ProblemDetailAdapter`：统一入口，默认 `DefaultProblemDetailAdapter` 使用核心 `ErrorResolutionPipeline` 解析异常并构建响应。
- `ProblemDetailBuilder`：封装 ProblemDetail 构造逻辑，处理路径提取、敏感信息脱敏、TraceId 写入及扩展字段。
- `GlobalRestExceptionHandler`：全局 REST 异常处理器，负责捕获所有异常并返回 `application/problem+json`；校验错误补充 `ErrorKeys.ERRORS`。
- `ValidationErrorsFormatter`：格式化 `BindingResult`，提供默认脱敏实现。
- `WebProblemFieldContributor`：Web 专用扩展 SPI，可按请求上下文补充字段（如 locale、客户端信息）。

### 3.2 使用规范
- Web 服务需引入本 starter 并配置 `patra.error.context-prefix`，以确保错误码前缀准确。
- 自定义 ProblemDetail 字段：实现 `ProblemFieldContributor`（核心通用）或 `WebProblemFieldContributor`（需要 request）。
- 如需关闭观测：可设置 `patra.error.observation.enabled=false`；若需自定义指标，实现 `ErrorObservationRecorder` 并覆写 Bean。
- 控制校验错误输出：通过重写 `ValidationErrorsFormatter` 实现业务定制。
## 4. Feign 适配层（patra-spring-cloud-starter-feign）

### 4.1 组件职责
- `ProblemDetailErrorDecoder`：解析下游 ProblemDetail 响应，自动补写 TraceId，支持宽容模式兜底；所有读/解析动作会记录观测指标。
- `FeignErrorObservationRecorder`：观测抽象，默认 `MicrometerFeignErrorObservationRecorder` 使用 MeterRegistry 记录解析耗时、宽容模式、响应体读取、TraceId 提取。
- `TraceIdRequestInterceptor`：将本地 TraceId 写入 Feign 请求头，保持链路一致。
- `RemoteCallException`：封装远端错误信息，适配层可据此进行语义判断；配套 `RemoteErrorHelper` 提供常用判断工具。

### 4.2 配置要点
- `FeignErrorProperties` 控制宽容模式（tolerant）、响应体最大读取字节与观测阈值。
- 观测可通过 `patra.feign.problem.observation.enabled` 开关禁用；若未注入 MeterRegistry，会自动降级为 NoOp。
- 若业务需扩展错误解码策略，可继承/替换 `ErrorDecoder`，但必须保持 ProblemDetail 解析规则与 TraceId 透传。
## 5. 配置总览

| 模块 | 关键属性前缀 | 说明 |
| --- | --- | --- |
| core | `patra.error.*` | 启动开关、上下文前缀、引擎行为、熔断、观测 |
| tracing | `patra.tracing.header-names` | TraceId 读取优先级列表 |
| web | `patra.web.problem.*` | ProblemDetail type 基础 URL、是否输出堆栈 |
| feign | `patra.feign.problem.*` | 宽容模式、观测阈值、响应体大小限制 |

> 建议统一在 Nacos/配置中心管理以上属性，确保多服务错误语义一致。
## 6. 扩展与最佳实践

- **扩展顺序**：尽量通过 SPI（`ErrorMappingContributor`、`ProblemFieldContributor` 等）扩展；仅在必要时替换默认 Bean。
- **错误码治理**：上下文前缀需在 patra-registry 中登记；错误码后缀必须与 HTTP 状态一致（0404→404）。
- **ProblemDetail 字段**：禁止泄露敏感信息；务必保持 `code`、`path`、`timestamp`、`traceId` 四个基础字段。
- **观测指标**：Micrometer 指标命名均以 `papertrace.error.*` 开头，可直接暴露至 Prometheus/Grafana；若集成其他系统，替换 Recorder 即可。
- **宽容模式策略**：Feign 默认启用 tolerant 模式，应结合重试/熔断策略谨慎配置，避免掩盖真实错误。
## 7. 验证与测试建议

1. 编写核心单元测试验证 `DefaultErrorResolutionEngine` 解析顺序、Command/Trait/命名兜底逻辑。
2. Web 层提供 REST 集成测试，模拟业务异常与校验错误，断言 ProblemDetail 字段齐全且含 TraceId。
3. Feign 端通过 MockServer 返回 ProblemDetail / 非标准响应，验证解码与观测指标是否按预期触发。
4. 使用统一的契约测试或 Postman Collection 覆盖跨服务调用，确保错误码与 HTTP 状态一致。
5. 观测链路：接入 Prometheus 后，检查 `papertrace.error.*` 指标是否按服务维度暴露；必要时配合日志采样。
## 8. 后续演进计划

- 建立错误码注册平台：由 patra-registry 提供错误码元数据服务，实现自动校验与可视化。
- 引入错误事件流（MQ）记录未知错误，结合 observability 做告警闭环。
- 支持多语言 ProblemDetail title/ detail 国际化，面向海外业务输出本地化提示。
- 与 API 网关协同，在入口统一注入 TraceId、处理全局异常，实现端到端一致性。

## 9. 模块落地示例：Ingest

- **错误码目录**：`patra-ingest-api/src/main/java/com/patra/ingest/api/error/IngestErrorCode.java` 定义了 ING-0xxx/1xxx 段错误码，用于区分 HTTP 对齐错误与采集业务错误。
- **映射组件**：`patra-ingest-boot/src/main/java/com/patra/ingest/config/IngestErrorMappingContributor.java` 将领域异常、调度参数错误、Outbox 状态异常映射到上述错误码，并透传远端 ProblemDetail。
- **配置约束**：`patra-ingest-boot/src/main/resources/ingest-error-config.yaml` 汇总 `patra.error.*` 与 `patra.feign.problem.*` 本地默认值，并在 `application.yaml` 通过 `spring.config.import` 引入，方便后续迁移至 Nacos。
- **远端调用**：`patra-ingest-adapter` 通过 `patra-spring-cloud-starter-feign` 的 `ProblemDetailErrorDecoder` 统一解析下游错误，`ProvenancePortAdapter` 仅在无法恢复的 4xx 情况下抛出 `IngestConfigurationException`。
- **计划编排**：`PlanIngestionApplicationService` 针对窗口解析、验证、装配、持久化等阶段抛出 `PlanValidationException`/`PlanAssemblyException`/`PlanPersistenceException`，对应 `ING-1403`/`ING-1601`/`ING-1503` 错误码，确保 ProblemDetail 可区分编排环节。
- **下一步建议**：为 Outbox/调度等场景补充单元测试，并在 Nacos 注册 ING 前缀，确保多环境配置一致。
