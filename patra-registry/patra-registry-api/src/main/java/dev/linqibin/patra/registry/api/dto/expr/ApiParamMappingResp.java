package dev.linqibin.patra.registry.api.dto.expr;

import java.time.Instant;

/// 表达式求值的 API 参数映射。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record ApiParamMappingResp(
    Long provenanceId,
    String operationType,
    String endpointName,
    String stdKey,
    String providerParamName,
    String transformCode,
    String notesJson,
    Instant effectiveFrom,
    Instant effectiveTo) {}
