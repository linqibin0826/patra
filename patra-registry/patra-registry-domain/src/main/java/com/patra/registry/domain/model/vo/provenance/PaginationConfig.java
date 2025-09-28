package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "Pagination config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String modeTrimmed = DomainValidationException.notBlank(paginationModeCode, "Pagination mode code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
        this.effectiveTo = effectiveTo;
        this.paginationModeCode = modeTrimmed;
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
