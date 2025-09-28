package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "Mapping id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String opTrimmed = DomainValidationException.notBlank(operationCode, "Operation code");
        String stdKeyTrimmed = DomainValidationException.notBlank(stdKey, "Standard key");
        String providerParamTrimmed = DomainValidationException.notBlank(providerParamName, "Provider param name");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.operationCode = opTrimmed;
        this.stdKey = stdKeyTrimmed;
        this.providerParamName = providerParamTrimmed;
        this.transformCode = transformCode != null ? transformCode.trim() : null;
        this.notesJson = notesJson;
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
        this.effectiveTo = effectiveTo;
    }

    /** 判断当前记录是否在给定时间点生效。 */
    public boolean isEffectiveAt(Instant instant) {
        DomainValidationException.nonNull(instant, "Instant");
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
