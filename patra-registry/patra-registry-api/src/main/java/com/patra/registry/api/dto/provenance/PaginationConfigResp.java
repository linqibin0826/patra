package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/// 分页和游标遍历配置。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record PaginationConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String paginationModeCode,
    Integer pageSizeValue,
    Integer maxPagesPerExecution,
    String sortFieldParamName,
    Integer sortingDirection) {}
