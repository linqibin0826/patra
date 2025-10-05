package com.patra.registry.domain.model.vo.expr;

import java.time.Instant;
import com.patra.registry.domain.exception.DomainValidationException;

/**
 * Domain value object for {@code reg_prov_expr_render_rule}.
 *
 * <p>Defines how an expression atom (field/op/match/negation/value-type) is rendered
 * into query fragments or standard parameters.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprRenderRule(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
        String fieldKey,
        String opCode,
        String matchTypeCode,
        Boolean negated,
        String valueTypeCode,
        String emitTypeCode,
        String matchTypeKey,
        String negatedKey,
        String valueTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        String template,
        String itemTemplate,
        String joiner,
        boolean wrapGroup,
        String paramsJson,
        String functionCode
) {
    public ExprRenderRule(Long id,
                          Long provenanceId,
                          String operationType,
                          String operationTypeKey,
                          String fieldKey,
                          String opCode,
                          String matchTypeCode,
                          Boolean negated,
                          String valueTypeCode,
                          String emitTypeCode,
                          String matchTypeKey,
                          String negatedKey,
                          String valueTypeKey,
                          Instant effectiveFrom,
                          Instant effectiveTo,
                          String template,
                          String itemTemplate,
                          String joiner,
                          boolean wrapGroup,
                          String paramsJson,
                          String functionCode) {
        if (id == null || id <= 0) throw new DomainValidationException("Render rule id must be positive");
        if (provenanceId == null || provenanceId <= 0) throw new DomainValidationException("Provenance id must be positive");
        if (fieldKey == null || fieldKey.isBlank()) throw new DomainValidationException("Field key cannot be blank");
        if (opCode == null || opCode.isBlank()) throw new DomainValidationException("Operation code cannot be blank");
        if (emitTypeCode == null || emitTypeCode.isBlank()) throw new DomainValidationException("Emit type code cannot be blank");
        if (matchTypeKey == null || matchTypeKey.isBlank()) throw new DomainValidationException("Match type key cannot be blank");
        if (negatedKey == null || negatedKey.isBlank()) throw new DomainValidationException("Negated key cannot be blank");
        if (valueTypeKey == null || valueTypeKey.isBlank()) throw new DomainValidationException("Value type key cannot be blank");
        if (effectiveFrom == null) throw new DomainValidationException("Effective from cannot be null");

        this.id = id;
        this.provenanceId = provenanceId;
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.fieldKey = fieldKey.trim();
        this.opCode = opCode.trim();
        this.matchTypeCode = matchTypeCode != null ? matchTypeCode.trim() : null;
        this.negated = negated;
        this.valueTypeCode = valueTypeCode != null ? valueTypeCode.trim() : null;
        this.emitTypeCode = emitTypeCode.trim();
        this.matchTypeKey = matchTypeKey.trim();
        this.negatedKey = negatedKey.trim();
        this.valueTypeKey = valueTypeKey.trim();
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.template = template;
        this.itemTemplate = itemTemplate;
        this.joiner = joiner != null ? joiner.trim() : null;
        this.wrapGroup = wrapGroup;
        this.paramsJson = paramsJson;
        this.functionCode = functionCode != null ? functionCode.trim() : null;
    }

    /** Checks whether the render rule is effective at the given instant. */
    public boolean isEffectiveAt(Instant instant) {
        if (instant == null) {
            throw new DomainValidationException("Instant cannot be null");
        }
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
