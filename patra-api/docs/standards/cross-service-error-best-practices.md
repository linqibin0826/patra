# 跨服务错误链路最佳实践（短文档）

目标：确保“抛错一致、出形一致、消费一致”，形成可观测、可治理、可回归的跨服务错误闭环。

## 一、端到端流程（建议）
- 服务端（下游）
  - 控制器/适配层：禁止返回 null；遇到“未找到/校验失败/冲突/限流/不可用”抛出相应领域异常；
  - 错误出形：统一由 Web 适配层输出 RFC7807 `ProblemDetail`，其中 `status = errorCode.httpStatus()`，`code = <CTX>-NNNN`；
  - 0xxx 段：使用 `HttpStdErrors.Group` 获取（按 `patra.error.context-prefix` 绑定），不在枚举维护。
- 客户端（上游）
  - Feign：`ProblemDetailErrorDecoder` 将下游错误解码为 `RemoteCallException`；
  - 语义判断：使用 `RemoteErrorHelper`（`isNotFound/isClientError/isServerError/isRetryable`）分类处理；
  - 降级与重试：
    - 404 → 判定为不可恢复配置缺失，立即抛出本地领域异常并终止流程；
    - 5xx/429/网络错误 → 重试/熔断/回退；
    - 4xx（非 404）→ 抛本地领域异常（不可恢复）。

## 二、错误码与状态
- 错误码接口：`ErrorCodeLike` 必须实现 `httpStatus()`；
- HTTP 对齐段（0xxx）：统一用 `HttpStdErrors`；
- 业务段（1xxx）：在各模块枚举中维护，明确 `httpStatus()`。

## 三、Do / Don’t
- Do：
  - 注入 `HttpStdErrors.Group`：避免在使用点硬编码 of("REG"/"ING")；
  - 在 Adapter 层抛领域异常，不返回 null；
  - 保持 `ProblemDetail` 字段：`code/path/timestamp/traceId`；
  - 在 Ingest 侧使用 `RemoteErrorHelper` 分类决定降级/抛错，其中“降级”仅限可恢复错误（如 5xx/429）。
- Don’t：
  - 不在枚举维护 0xxx；不再实现/依赖 `StatusMappingStrategy`；
  - 不在领域模型中硬编码 HTTP 概念（由错误码承载）；
  - 不吞异常导致 200 + 空体。

## 四、最小示例

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

## 五、配置要点
- `patra.error.context-prefix`: 各服务必须配置（如 REG/ING）；
- `patra.feign.problem.*`: 启用宽容模式、响应体大小与观测阈值按需；
- `patra.tracing.header-names`: 统一 TraceId 读取顺序。

## 六、观测与回归
- Micrometer 指标：`papertrace.error.*`；
- 回归建议：
  - e2e 覆盖“下游 ProblemDetail → Feign 解码 → 上游降级/抛错”；
  - 断言 `status=errorCode.httpStatus()` 与 `code` 前缀一致；
  - 校验 traceId 贯通与错误日志结构化。 
