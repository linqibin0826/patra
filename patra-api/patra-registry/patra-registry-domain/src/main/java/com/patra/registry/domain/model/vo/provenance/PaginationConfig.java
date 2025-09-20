package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_pagination_cfg} 的领域值对象。
 */
public record PaginationConfig(
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
    public PaginationConfig(Long id,
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
                            String totalCountXpath) {
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

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.paginationModeCode = paginationModeCode.trim();
        this.pageSizeValue = pageSizeValue;
        this.maxPagesPerExecution = maxPagesPerExecution;
        this.pageNumberParamName = pageNumberParamName != null ? pageNumberParamName.trim() : null;
        this.pageSizeParamName = pageSizeParamName != null ? pageSizeParamName.trim() : null;
        this.startPageNumber = startPageNumber;
        this.sortFieldParamName = sortFieldParamName != null ? sortFieldParamName.trim() : null;
        this.sortDirection = sortDirection != null ? sortDirection.trim() : null;
        this.cursorParamName = cursorParamName != null ? cursorParamName.trim() : null;
        this.initialCursorValue = initialCursorValue != null ? initialCursorValue.trim() : null;
        this.nextCursorJsonpath = nextCursorJsonpath != null ? nextCursorJsonpath.trim() : null;
        this.hasMoreJsonpath = hasMoreJsonpath != null ? hasMoreJsonpath.trim() : null;
        this.totalCountJsonpath = totalCountJsonpath != null ? totalCountJsonpath.trim() : null;
        this.nextCursorXpath = nextCursorXpath != null ? nextCursorXpath.trim() : null;
        this.hasMoreXpath = hasMoreXpath != null ? hasMoreXpath.trim() : null;
        this.totalCountXpath = totalCountXpath != null ? totalCountXpath.trim() : null;
    }
}
