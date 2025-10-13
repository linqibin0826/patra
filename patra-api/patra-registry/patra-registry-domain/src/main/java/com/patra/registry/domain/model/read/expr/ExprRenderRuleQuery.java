package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Expression render rule query view.
 *
 * <p>Read-optimized projection for querying expression-to-query transformation rules.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprRenderRuleQuery(
    Long provenanceId,
    String operationType,
    String fieldKey,
    String opCode,
    String matchTypeCode,
    Boolean negated,
    String valueTypeCode,
    String emitTypeCode,
    String template,
    String itemTemplate,
    String joiner,
    boolean wrapGroup,
    String paramsJson,
    String functionCode,
    Instant effectiveFrom,
    Instant effectiveTo) {
  public ExprRenderRuleQuery {
    DomainValidationException.positive(provenanceId, "Provenance id");
    fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
    opCode = DomainValidationException.notBlank(opCode, "Operation code");
    emitTypeCode = DomainValidationException.notBlank(emitTypeCode, "Emit type code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
    operationType = operationType != null ? operationType.trim() : null;
    matchTypeCode = matchTypeCode != null ? matchTypeCode.trim() : null;
    valueTypeCode = valueTypeCode != null ? valueTypeCode.trim() : null;
  }
}
