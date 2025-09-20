package com.patra.registry.contract.query.view.provenance;

import java.time.Instant;

/**
 * 分页配置查询视图。
 */
public record PaginationConfigQuery(
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
    public PaginationConfigQuery {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Pagination config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (paginationModeCode == null || paginationModeCode.isBlank()) {
            throw new IllegalArgumentException("Pagination mode code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        paginationModeCode = paginationModeCode.trim();
        pageNumberParamName = pageNumberParamName != null ? pageNumberParamName.trim() : null;
        pageSizeParamName = pageSizeParamName != null ? pageSizeParamName.trim() : null;
        sortFieldParamName = sortFieldParamName != null ? sortFieldParamName.trim() : null;
        sortDirection = sortDirection != null ? sortDirection.trim() : null;
        cursorParamName = cursorParamName != null ? cursorParamName.trim() : null;
        initialCursorValue = initialCursorValue != null ? initialCursorValue.trim() : null;
        nextCursorJsonpath = nextCursorJsonpath != null ? nextCursorJsonpath.trim() : null;
        hasMoreJsonpath = hasMoreJsonpath != null ? hasMoreJsonpath.trim() : null;
        totalCountJsonpath = totalCountJsonpath != null ? totalCountJsonpath.trim() : null;
        nextCursorXpath = nextCursorXpath != null ? nextCursorXpath.trim() : null;
        hasMoreXpath = hasMoreXpath != null ? hasMoreXpath.trim() : null;
        totalCountXpath = totalCountXpath != null ? totalCountXpath.trim() : null;
    }
}
