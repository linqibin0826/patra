package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "Endpoint definition id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String nameTrimmed = DomainValidationException.notBlank(endpointName, "Endpoint name");
        String usageTrimmed = DomainValidationException.notBlank(endpointUsageCode, "Endpoint usage code");
        String methodTrimmed = DomainValidationException.notBlank(httpMethodCode, "HTTP method code");
        String pathTrimmed = DomainValidationException.notBlank(pathTemplate, "Path template");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.endpointName = nameTrimmed;
        this.endpointUsageCode = usageTrimmed;
        this.httpMethodCode = methodTrimmed;
        this.pathTemplate = pathTrimmed;
        this.defaultQueryParamsJson = defaultQueryParamsJson;
        this.defaultBodyPayloadJson = defaultBodyPayloadJson;
        this.requestContentType = requestContentType != null ? requestContentType.trim() : null;
        this.authRequired = authRequired;
        this.credentialHintName = credentialHintName != null ? credentialHintName.trim() : null;
        this.pageParamName = pageParamName != null ? pageParamName.trim() : null;
        this.pageSizeParamName = pageSizeParamName != null ? pageSizeParamName.trim() : null;
        this.cursorParamName = cursorParamName != null ? cursorParamName.trim() : null;
        this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
        this.effectiveTo = effectiveTo;
    }

    public boolean isScopedToTask() {
        return "TASK".equalsIgnoreCase(scopeCode);
    }
}
