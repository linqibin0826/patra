package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 分页配置响应 DTO。
 */
public record PaginationConfigResp(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        String paginationModeCode,
        Integer pageSizeValue,
        Integer maxPagesPerExecution,
        String pageNumberParamName,
        String pageSizeParamName,
        Integer startPageNumber,
        String sortFieldParamName,
        String sortDirection,
        String cursorParamName,
        String initialCursorValue,
        String nextCursorJsonpath,
        String hasMoreJsonpath,
        String totalCountJsonpath,
        String nextCursorXpath,
        String hasMoreXpath,
        String totalCountXpath
) {
}
