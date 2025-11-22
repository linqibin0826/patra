package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/// 数据源 API 请求的批处理配置。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record BatchingConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer detailFetchBatchSize,
    String idsParamName,
    String idsJoinDelimiter,
    Integer maxIdsPerRequest) {}
