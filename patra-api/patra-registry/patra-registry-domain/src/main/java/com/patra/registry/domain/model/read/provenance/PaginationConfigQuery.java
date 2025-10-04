package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 分页配置查询视图。
 */
public record PaginationConfigQuery(
        Long id,
        Long provenanceId,
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
            throw new DomainValidationException("Pagination config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (paginationModeCode == null || paginationModeCode.isBlank()) {
            throw new DomainValidationException("Pagination mode code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
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
