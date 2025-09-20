package com.patra.registry.domain.model.vo.expr;

import java.time.Instant;

/**
 * 表 {@code reg_prov_api_param_map} 的领域值对象。
 */
public record ApiParamMapping(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        String operationCode,
        String stdKey,
        String providerParamName,
        String transformCode,
        String notesJson,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public ApiParamMapping(Long id,
                           Long provenanceId,
                           String scopeCode,
                           String taskType,
                           String taskTypeKey,
                           String operationCode,
                           String stdKey,
                           String providerParamName,
                           String transformCode,
                           String notesJson,
                           Instant effectiveFrom,
                           Instant effectiveTo) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Mapping id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (operationCode == null || operationCode.isBlank()) {
            throw new IllegalArgumentException("Operation code cannot be blank");
        }
        if (stdKey == null || stdKey.isBlank()) {
            throw new IllegalArgumentException("Standard key cannot be blank");
        }
        if (providerParamName == null || providerParamName.isBlank()) {
            throw new IllegalArgumentException("Provider param name cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.operationCode = operationCode.trim();
        this.stdKey = stdKey.trim();
        this.providerParamName = providerParamName.trim();
        this.transformCode = transformCode != null ? transformCode.trim() : null;
        this.notesJson = notesJson;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** 判断当前记录是否在给定时间点生效。 */
    public boolean isEffectiveAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Instant cannot be null");
        }
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
