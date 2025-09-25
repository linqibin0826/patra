package com.patra.registry.domain.model.read.expr;

import java.time.Instant;

/**
 * API 参数映射查询视图。
 */
public record ApiParamMappingQuery(
        Long provenanceId,
        String scopeCode,
        String taskType,
        String operationCode,
        String stdKey,
        String providerParamName,
        String transformCode,
        String notesJson,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public ApiParamMappingQuery {
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
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        operationCode = operationCode.trim();
        stdKey = stdKey.trim();
        providerParamName = providerParamName.trim();
        transformCode = transformCode != null ? transformCode.trim() : null;
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }
    }
}
