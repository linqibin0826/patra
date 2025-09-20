package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_endpoint_def} 的领域值对象。
 */
public record EndpointDefinition(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        String endpointName,
        String endpointUsageCode,
        String httpMethodCode,
        String pathTemplate,
        String defaultQueryParamsJson,
        String defaultBodyPayloadJson,
        String requestContentType,
        boolean authRequired,
        String credentialHintName,
        String pageParamName,
        String pageSizeParamName,
        String cursorParamName,
        String idsParamName,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public EndpointDefinition(Long id,
                              Long provenanceId,
                              String scopeCode,
                              String taskType,
                              String taskTypeKey,
                              String endpointName,
                              String endpointUsageCode,
                              String httpMethodCode,
                              String pathTemplate,
                              String defaultQueryParamsJson,
                              String defaultBodyPayloadJson,
                              String requestContentType,
                              boolean authRequired,
                              String credentialHintName,
                              String pageParamName,
                              String pageSizeParamName,
                              String cursorParamName,
                              String idsParamName,
                              Instant effectiveFrom,
                              Instant effectiveTo) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Endpoint definition id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (endpointName == null || endpointName.isBlank()) {
            throw new IllegalArgumentException("Endpoint name cannot be blank");
        }
        if (endpointUsageCode == null || endpointUsageCode.isBlank()) {
            throw new IllegalArgumentException("Endpoint usage code cannot be blank");
        }
        if (httpMethodCode == null || httpMethodCode.isBlank()) {
            throw new IllegalArgumentException("HTTP method code cannot be blank");
        }
        if (pathTemplate == null || pathTemplate.isBlank()) {
            throw new IllegalArgumentException("Path template cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.endpointName = endpointName.trim();
        this.endpointUsageCode = endpointUsageCode.trim();
        this.httpMethodCode = httpMethodCode.trim();
        this.pathTemplate = pathTemplate.trim();
        this.defaultQueryParamsJson = defaultQueryParamsJson;
        this.defaultBodyPayloadJson = defaultBodyPayloadJson;
        this.requestContentType = requestContentType != null ? requestContentType.trim() : null;
        this.authRequired = authRequired;
        this.credentialHintName = credentialHintName != null ? credentialHintName.trim() : null;
        this.pageParamName = pageParamName != null ? pageParamName.trim() : null;
        this.pageSizeParamName = pageSizeParamName != null ? pageSizeParamName.trim() : null;
        this.cursorParamName = cursorParamName != null ? cursorParamName.trim() : null;
        this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public boolean isScopedToTask() {
        return "TASK".equalsIgnoreCase(scopeCode);
    }
}
