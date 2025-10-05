package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_api_param_map}.
 *
 * <p>Maps unified standard keys to provider-specific parameter names at SOURCE/TASK scope.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMapping(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
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
                           String operationType,
                           String operationTypeKey,
                           String operationCode,
                           String stdKey,
                           String providerParamName,
                           String transformCode,
                           String notesJson,
                           Instant effectiveFrom,
                           Instant effectiveTo) {
        DomainValidationException.positive(id, "Mapping id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String opTrimmed = DomainValidationException.notBlank(operationCode, "Operation code");
        String stdKeyTrimmed = DomainValidationException.notBlank(stdKey, "Standard key");
        String providerParamTrimmed = DomainValidationException.notBlank(providerParamName, "Provider param name");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // already validated
        this.provenanceId = provenanceId; // already validated
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.operationCode = opTrimmed;
        this.stdKey = stdKeyTrimmed;
        this.providerParamName = providerParamTrimmed;
        this.transformCode = transformCode != null ? transformCode.trim() : null;
        this.notesJson = notesJson;
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
        this.effectiveTo = effectiveTo;
    }

    /** Checks whether the mapping is effective at the given instant. */
    public boolean isEffectiveAt(Instant instant) {
        DomainValidationException.nonNull(instant, "Instant");
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
