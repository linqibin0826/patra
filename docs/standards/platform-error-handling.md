# 平台级错误处理规范

本文定义 Papertrace 平台统一的错误处理体系，指导核心模块（core）、Web
适配层（web）与远程调用适配层（feign）的设计、配置与扩展方式，确保所有微服务输出一致、可观测、可治理的错误响应。

## 1. 总体目标

- **统一语义**：所有错误必须映射到平台错误码（`<上下文前缀>-<HTTP 后缀>`），并产出 RFC 7807 ProblemDetail 结构。
- **可观测性**：核心解析与各适配层通过 Micrometer 暴露指标，可按需降级为 NoOp。
- **可扩展性**：通过 SPI / 拦截器组合实现解耦；业务可在不破坏主干的前提下定制策略。
- **链路关联**：TraceId 自主注入与透传，保证跨服务诊断能力。

## 2. 核心模块（patra-spring-boot-starter-core）

### 2.1 关键组件

- `ErrorResolutionEngine`：纯解析内核，默认实现 `DefaultErrorResolutionEngine` 负责按照 ApplicationException →
  ErrorMappingContributor → Trait → 命名启发式 → 兜底 的顺序解析异常。
- `ErrorResolutionPipeline`：拦截器管线，以责任链形式组合追踪、熔断、指标等横切能力。
- `ResolutionInterceptor`：核心拦截 SPI，当前提供 `TracingInterceptor`、`CircuitBreakerInterceptor`、`MetricsInterceptor`。
- `ErrorObservationRecorder`：观测抽象，默认 `MicrometerErrorObservationRecorder`，无 MeterRegistry 时回退 `NO_OP`。
- 配置集中于 `ErrorProperties`（引擎行为、观测、熔断）与 `TracingProperties`（TraceId 读取头）。

### 2.2 扩展规则

- 业务若需自定义错误码映射，应实现 `ErrorMappingContributor` 并注册为 Bean。
- 状态获取：错误码自身提供 `httpStatus()`；HTTP 对齐段（0xxx）通过 `HttpStdErrors` 工厂按前缀生成（推荐注入
  `HttpStdErrors.Group` 使用，避免硬编码 of("ING"/"REG")）。
- 观测：若接入第三方指标体系，可自定义 `ErrorObservationRecorder` 替换默认实现。
- 遵循六边形：核心模块不引入 Web/Feign 依赖，保持纯粹。
- 已移除：`StatusMappingStrategy` 相关配置与示例均废弃，请勿再实现/依赖该接口。

### 2.3 0xxx 获取与使用示例（强烈推荐）

- 建议通过注入 `HttpStdErrors.Group` 获取 0xxx 段标准错误码，前缀由 `patra.error.context-prefix` 提供：

```java

@Component
@RequiredArgsConstructor
class DemoMappingContributor implements ErrorMappingContributor {
    private final HttpStdErrors.Group http; // 自动按前缀绑定（REG/ING/...）

    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        if (exception instanceof DuplicateKeyException) {
            return Optional.of(http.CONFLICT()); // 409 → REG-0409 / ING-0409
        }
        if (exception instanceof DataIntegrityViolationException) {
            return Optional.of(http.UNPROCESSABLE()); // 422 → REG-0422 / ING-0422
        }
        return Optional.empty();
    }
}
```

- 禁止在各模块的错误码枚举中维护 0xxx 常量；禁止通过 `HttpStdErrors.of("REG")` 等在使用点硬编码前缀。

### 2.4 返回 null 的反例（Anti‑Pattern）

- 控制器/适配层在“未找到/不可用”场景禁止返回 `null`，应抛出相应领域异常（如 `DictionaryNotFoundException`、
  `ProvenanceNotFoundException`），由统一 Web 适配层输出 `ProblemDetail`（0xxx 由工厂提供）。
- 这样可确保：HTTP 状态正确、下游 Feign 能解码为 `RemoteCallException`，上游可以按语义降级/重试。

## 3. Web 适配层（patra-spring-boot-starter-web）

### 3.1 组件职责

- `ProblemDetailAdapter`：统一入口，默认 `DefaultProblemDetailAdapter` 使用核心 `ErrorResolutionPipeline` 解析异常并构建响应。
- `ProblemDetailBuilder`：封装 ProblemDetail 构造逻辑，处理路径提取、敏感信息脱敏、TraceId 写入及扩展字段。
- `GlobalRestExceptionHandler`：全局 REST 异常处理器，负责捕获所有异常并返回 `application/problem+json`；校验错误补充
  `ErrorKeys.ERRORS`。
- `ValidationErrorsFormatter`：格式化 `BindingResult`，提供默认脱敏实现。
- `WebProblemFieldContributor`：Web 专用扩展 SPI，可按请求上下文补充字段（如 locale、客户端信息）。

### 3.2 使用规范

- Web 服务需引入本 starter 并配置 `patra.error.context-prefix`，以确保错误码前缀准确。
- 自定义 ProblemDetail 字段：实现 `ProblemFieldContributor`（核心通用）或 `WebProblemFieldContributor`（需要 request）。
- 如需关闭观测：可设置 `patra.error.observation.enabled=false`；若需自定义指标，实现 `ErrorObservationRecorder` 并覆写
  Bean。
- 控制校验错误输出：通过重写 `ValidationErrorsFormatter` 实现业务定制。

## 4. Feign 适配层（patra-spring-cloud-starter-feign）

### 4.1 组件职责

- `ProblemDetailErrorDecoder`：解析下游 ProblemDetail 响应，自动补写 TraceId，支持宽容模式兜底；所有读/解析动作会记录观测指标。
- `FeignErrorObservationRecorder`：观测抽象，默认 `MicrometerFeignErrorObservationRecorder` 使用 MeterRegistry
  记录解析耗时、宽容模式、响应体读取、TraceId 提取。
- `TraceIdRequestInterceptor`：将本地 TraceId 写入 Feign 请求头，保持链路一致。
- `RemoteCallException`：封装远端错误信息，适配层可据此进行语义判断；配套 `RemoteErrorHelper` 提供常用判断工具。

### 4.2 配置要点

- `FeignErrorProperties` 控制宽容模式（tolerant）、响应体最大读取字节与观测阈值。
- 观测可通过 `patra.feign.problem.observation.enabled` 开关禁用；若未注入 MeterRegistry，会自动降级为 NoOp。
- 若业务需扩展错误解码策略，可继承/替换 `ErrorDecoder`，但必须保持 ProblemDetail 解析规则与 TraceId 透传。

## 5. 配置总览

| 模块      | 关键属性前缀                       | 说明                               |
|---------|------------------------------|----------------------------------|
| core    | `patra.error.*`              | 启动开关、上下文前缀、引擎行为、熔断、观测            |
| tracing | `patra.tracing.header-names` | TraceId 读取优先级列表                  |
| web     | `patra.web.problem.*`        | ProblemDetail type 基础 URL、是否输出堆栈 |
| feign   | `patra.feign.problem.*`      | 宽容模式、观测阈值、响应体大小限制                |

> 建议统一在 Nacos/配置中心管理以上属性，确保多服务错误语义一致。

## 6. 扩展与最佳实践

- **扩展顺序**：尽量通过 SPI（`ErrorMappingContributor`、`ProblemFieldContributor` 等）扩展；仅在必要时替换默认 Bean。
- **错误码治理**：上下文前缀需在 patra-registry 中登记；错误码后缀必须与 HTTP 状态一致（0404→404）。
- **ProblemDetail 字段**：禁止泄露敏感信息；务必保持 `code`、`path`、`timestamp`、`traceId` 四个基础字段。
- **观测指标**：Micrometer 指标命名均以 `papertrace.error.*` 开头，可直接暴露至 Prometheus/Grafana；若集成其他系统，替换
  Recorder 即可。
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

- **错误码目录**：`patra-ingest-api/src/main/java/com/patra/ingest/api/error/IngestErrorCode.java` 定义 ING-1xxx
  段业务错误码；HTTP 对齐段（0xxx）通过 `HttpStdErrors` 工厂（按 `patra.error.context-prefix` 自动前缀）生成，用于区分 HTTP
  对齐错误与采集业务错误。
- **映射组件**：`patra-ingest-boot/src/main/java/com/patra/ingest/config/IngestErrorMappingContributor.java`
  将领域异常、调度参数错误、Outbox 状态异常映射到上述错误码，并透传远端 ProblemDetail。
- **配置约束**：`patra-ingest-boot/src/main/resources/ingest-error-config.yaml` 汇总 `patra.error.*` 与
  `patra.feign.problem.*` 本地默认值，并在 `application.yaml` 通过 `spring.config.import` 引入，方便后续迁移至 Nacos。
- **远端调用**：`patra-ingest-infra` 统一承载出站 RPC；通过 `patra-spring-cloud-starter-feign` 的
  `ProblemDetailErrorDecoder` 解析下游错误。
  领域端口为 `PatraRegistryPort`，实现类 `infra.rpc.registry.ProvenancePortRpcAdapter`：
    - 远端 4xx/配置缺失：抛出 `IngestConfigurationException`；
    - 远端 5xx/网络异常：记录 traceId/远端错误码后可降级返回“最小可用快照”，或上抛由上层重试策略处理。
- **计划编排**：`PlanIngestionApplicationService` 针对窗口解析、验证、装配、持久化等阶段抛出 `PlanValidationException`/
  `PlanAssemblyException`/`PlanPersistenceException`，对应 `ING-1403`/`ING-1601`/`ING-1503` 错误码，确保 ProblemDetail
  可区分编排环节。
- **下一步建议**：为 Outbox/调度等场景补充单元测试，并在 Nacos 注册 ING 前缀，确保多环境配置一致。
-

## 10. 模块落地示例：Registry

- **错误码目录**：`patra-registry-api/src/main/java/com/patra/registry/api/error/RegistryErrorCode.java` 维护 REG-1xxx
  段；HTTP 对齐段（0xxx）由 `HttpStdErrors` 工厂提供，`patra-registry-boot/src/main/resources/registry-error-config.yaml`
  提供本地登记，后续迁移至 Nacos。
- **映射组件**：`patra-registry-boot/src/main/java/com/patra/registry/config/RegistryErrorMappingContributor.java`
  负责将领域异常（如 `DictionaryValidationException`、`DictionaryNotFoundException`）映射为 REG-140x/150x 错误码。
- **领域异常应用**：应用/适配层统一抛出领域异常；`DictionaryQueryAppService`、`DictionaryValidationAppService` 用
  `DictionaryValidationException` 替换 `IllegalArgumentException`，确保 ProblemDetail 能返回 REG-1407，同时包含 type/item
  上下文。
- **Web/Feign 适配**：`DictionaryClientImpl` 捕获领域异常进行结构化日志，异常交由 `ProblemDetailAdapter` 输出，保持
  RFC7807。
- **配置约束**：`application.yaml` 经由 `spring.config.import` 引入新的 `registry-error-config.yaml`，本地即可启用统一引擎、观测与熔断策略。
- **后续建议**：补充字典查询/校验用例的 ProblemDetail 集成测试，与 `patra-registry` 未来的错误码注册中心实现联动校验。

## 11. 跨服务错误链路最佳实践

目标：确保“抛错一致、出形一致、消费一致”，形成可观测、可治理、可回归的跨服务错误闭环。

### 11.1 端到端流程（建议）
- 服务端（下游）
  - 控制器/适配层：禁止返回 null；遇到“未找到/校验失败/冲突/限流/不可用”抛出相应领域异常；
  - 错误出形：统一由 Web 适配层输出 RFC7807 `ProblemDetail`，其中 `status = errorCode.httpStatus()`，`code = <CTX>-NNNN`；
  - 0xxx 段：使用 `HttpStdErrors.Group` 获取（按 `patra.error.context-prefix` 绑定），不在枚举维护。
- 客户端（上游）
  - Feign：`ProblemDetailErrorDecoder` 将下游错误解码为 `RemoteCallException`；
  - 语义判断：使用 `RemoteErrorHelper`（`isNotFound/isClientError/isServerError/isRetryable`）分类处理；
  - 降级与重试：404 直接失败；5xx/429/网络错误按策略重试/熔断；其他 4xx 抛本地领域异常。

### 11.2 错误码与状态
- 错误码接口：`ErrorCodeLike` 必须实现 `httpStatus()`；
- HTTP 对齐段（0xxx）：统一用 `HttpStdErrors`；
- 业务段（1xxx）：在各模块枚举中维护，明确 `httpStatus()`。

### 11.3 Do / Don’t
- Do：注入 `HttpStdErrors.Group`；在 Adapter 层抛领域异常；保持 ProblemDetail 的 `code/path/timestamp/traceId` 字段；
  在 Ingest 侧使用 `RemoteErrorHelper` 分类决定降级/抛错。
- Don’t：在枚举维护 0xxx；实现/依赖已废弃的 `StatusMappingStrategy`；在领域模型中硬编码 HTTP 概念；吞异常导致 200 + 空体。

### 11.4 最小示例

```java
// 下游（Registry）示例：未找到 → 抛领域异常 → REG-0404（http 404）
@RestController
@RequiredArgsConstructor
class ProvenanceClientImpl implements ProvenanceClient {
    private final ProvenanceConfigAppService appService; private final ProvenanceApiConverter converter;
    @Override public ProvenanceResp getProvenance(ProvenanceCode code) {
        return appService.findProvenance(code)
            .map(converter::toResp)
            .orElseThrow(() -> new ProvenanceNotFoundException("Provenance not found: code=" + code));
    }
}

// 上游（Ingest）示例：远端错误分类
private ProvenanceConfigSnapshot handleRemote(RemoteCallException ex, String code, String operationType, String endpoint) {
    if (RemoteErrorHelper.isNotFound(ex)) {
        String msg = String.format("Provenance config not found, code=%s, operationType=%s, endpoint=%s", code, operationType, endpoint);
        throw new IngestConfigurationException(code, operationType, endpoint, msg, ex);
    }
    if (RemoteErrorHelper.isServerError(ex) || RemoteErrorHelper.isRetryable(ex)) {
        return createMinimalSnapshot(code);
    }
    String msg = String.format(
            "Registry client error, code=%s, status=%d, remoteCode=%s, traceId=%s",
            code, ex.getHttpStatus(), ex.getErrorCode(), ex.getTraceId());
    throw new IngestConfigurationException(code, operationType, endpoint, msg, ex);
}
```

### 11.5 配置要点与观测
- `patra.error.context-prefix`：各服务必须配置（如 REG/ING）。
- `patra.feign.problem.*`：宽容模式、响应体大小与观测阈值按需设置。
- `patra.tracing.header-names`：统一 TraceId 读取顺序。
- 指标命名：`papertrace.error.*`；建议通过 Prometheus/Grafana 监控端到端错误链路。

## 12. 参考资料


- 相关：Feign API 设计指南、日志规范。
