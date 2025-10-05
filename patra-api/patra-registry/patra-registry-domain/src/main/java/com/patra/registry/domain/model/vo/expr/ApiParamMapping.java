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
        /* Primary key; unique mapping identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL/SANDBOX); {@code null} applies to all */
        String operationType,
        /* Normalized operation type key; defaults to {@code ALL} when {@code operationType} is {@code null} */
        String operationTypeKey,
        /* Endpoint operation code (DICT CODE: reg_operation) such as SEARCH/DETAIL/LOOKUP */
        String operationCode,
        /* Standard key (unified internal semantic key) typically produced during rendering (e.g., from/to/ti/ab) */
        String stdKey,
        /* Provider parameter name: concrete HTTP parameter (e.g., mindate/maxdate/term/retmax) */
        String providerParamName,
        /* Optional value-level transform code (DICT CODE: reg_transform) such as TO_EXCLUSIVE_MINUS_1D */
        String transformCode,
        /* Additional notes as JSON object for platform differences/boundaries */
        String notesJson,
        /* Inclusive timestamp marking when this mapping becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this mapping expires; {@code null} means open-ended */
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
        this.effectiveFrom = effectiveFrom; // already validated as non-null
        this.effectiveTo = effectiveTo;
    }

    /**
     * Checks whether the mapping is effective at the given instant.
     *
     * @param instant the time point to check (must not be null)
     * @return {@code true} if the mapping is effective at the given instant
     * @throws DomainValidationException if {@code instant} is null
     */
    public boolean isEffectiveAt(Instant instant) {
        DomainValidationException.nonNull(instant, "Instant");
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
