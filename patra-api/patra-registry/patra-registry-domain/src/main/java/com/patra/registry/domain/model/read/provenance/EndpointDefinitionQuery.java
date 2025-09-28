package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 端点定义查询视图。
 */
public record EndpointDefinitionQuery(
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
    public EndpointDefinitionQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Endpoint id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new DomainValidationException("Scope code cannot be blank");
        }
        if (endpointName == null || endpointName.isBlank()) {
            throw new DomainValidationException("Endpoint name cannot be blank");
        }
        if (endpointUsageCode == null || endpointUsageCode.isBlank()) {
            throw new DomainValidationException("Endpoint usage code cannot be blank");
        }
        if (httpMethodCode == null || httpMethodCode.isBlank()) {
            throw new DomainValidationException("HTTP method code cannot be blank");
        }
        if (pathTemplate == null || pathTemplate.isBlank()) {
            throw new DomainValidationException("Path template cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        endpointName = endpointName.trim();
        endpointUsageCode = endpointUsageCode.trim();
        httpMethodCode = httpMethodCode.trim();
        pathTemplate = pathTemplate.trim();
        requestContentType = requestContentType != null ? requestContentType.trim() : null;
        credentialHintName = credentialHintName != null ? credentialHintName.trim() : null;
        pageParamName = pageParamName != null ? pageParamName.trim() : null;
        pageSizeParamName = pageSizeParamName != null ? pageSizeParamName.trim() : null;
        cursorParamName = cursorParamName != null ? cursorParamName.trim() : null;
        idsParamName = idsParamName != null ? idsParamName.trim() : null;
    }
}
