package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.support.TemporalEntity;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_api_param_map}.
 *
 * <p>Map unified standard keys to provider-specific parameter names at SOURCE/TASK scope. Only
 * responsible for key-name mapping; value-level transform is declared via transform_code only.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMapping(
    /* Primary key; unique mapping identifier */
    Long id,
    /* Foreign key referencing reg_provenance.id */
    Long provenanceId,
    /* Operation type discriminator (HARVEST/UPDATE/BACKFILL/SANDBOX); null applies to all */
    String operationType,
    /* Endpoint name this mapping applies to; null means all endpoints */
    String endpointName,
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
    /* Exclusive timestamp marking when this mapping expires; null means open-ended */
    Instant effectiveTo)
    implements TemporalEntity {
  /**
   * Canonical constructor with validation.
   *
   * @param id unique mapping identifier, must be positive
   * @param provenanceId provenance identifier, must be positive
   * @param operationType operation type discriminator, nullable
   * @param endpointName endpoint name this mapping applies to, nullable (null means all endpoints)
   * @param stdKey standard key, must not be blank
   * @param providerParamName provider parameter name, must not be blank
   * @param transformCode transform code from dictionary, nullable
   * @param notesJson additional notes as JSON, nullable
   * @param effectiveFrom effective start timestamp, must not be null
   * @param effectiveTo effective end timestamp, nullable (open-ended)
   * @throws DomainValidationException if validation fails
   */
  public ApiParamMapping(
      Long id,
      Long provenanceId,
      String operationType,
      String endpointName,
      String stdKey,
      String providerParamName,
      String transformCode,
      String notesJson,
      Instant effectiveFrom,
      Instant effectiveTo) {
    DomainValidationException.positive(id, "Mapping id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    String stdKeyTrimmed = DomainValidationException.notBlank(stdKey, "Standard key");
    String providerParamTrimmed =
        DomainValidationException.notBlank(providerParamName, "Provider param name");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.endpointName = endpointName != null ? endpointName.trim() : null;
    this.stdKey = stdKeyTrimmed;
    this.providerParamName = providerParamTrimmed;
    this.transformCode = transformCode != null ? transformCode.trim() : null;
    this.notesJson = notesJson;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
  }
}
