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
        /* Primary key; unique render rule identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
        String operationType,
        /* Unified internal field key (logical FK to {@code reg_expr_field_dict.field_key}) */
        String fieldKey,
        /* Expression operator code (DICT CODE: reg_expr_op) such as TERM/IN/RANGE/EXISTS/TOKEN */
        String opCode,
        /* Match type code (DICT CODE: reg_match_type; TERM only) such as PHRASE/EXACT/ANY; {@code null} means agnostic */
        String matchTypeCode,
        /* Negation flag: {@code true} for NOT, {@code false} for non-NOT; {@code null} means agnostic */
        Boolean negated,
        /* Value type code for RANGE etc. (STRING/DATE/DATETIME/NUMBER); {@code null} means agnostic */
        String valueTypeCode,
        /* Emission type (DICT CODE: reg_emit_type): QUERY for query fragment, PARAMS for standard params */
        String emitTypeCode,
        /* Normalization of {@code matchTypeCode}: {@code null} → {@code ANY} */
        String matchTypeKey,
        /* Normalization of {@code negated}: {@code null} → {@code ANY}, {@code true} → {@code T}, {@code false} → {@code F} */
        String negatedKey,
        /* Normalization of {@code valueTypeCode}: {@code null} → {@code ANY} */
        String valueTypeKey,
        /* Inclusive timestamp marking when this rule becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this rule expires; {@code null} means open-ended */
        Instant effectiveTo,
        /* Template to render query fragment when {@code emitTypeCode} is {@code QUERY}; supports helpers (e.g., {{q v}}/{{lower ...}}) */
        String template,
        /* Template for each item when {@code emitTypeCode} is {@code QUERY} and {@code opCode} is {@code IN} */
        String itemTemplate,
        /* Joiner for items when {@code emitTypeCode} is {@code QUERY} and {@code opCode} is {@code IN} (e.g., " OR " / " AND ") */
        String joiner,
        /* Whether to wrap entire group in parentheses when {@code emitTypeCode} is {@code QUERY} and {@code opCode} is {@code IN} */
        boolean wrapGroup,
        /* JSON of standard keys/template variables when {@code emitTypeCode} is {@code PARAMS} (e.g., {"from":"from","to":"to"}) */
        String paramsJson,
        /* Template-level render function code (subset/extension of reg_transform); e.g., PUBMED_DATETYPE */
        String functionCode
) {
    public ExprRenderRule(Long id,
                          Long provenanceId,
                          String operationType,
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

    /**
     * Checks whether the render rule is effective at the given instant.
     *
     * @param instant the time point to check (must not be null)
     * @return {@code true} if the rule is effective at the given instant
     * @throws DomainValidationException if {@code instant} is null
     */
    public boolean isEffectiveAt(Instant instant) {
        if (instant == null) {
            throw new DomainValidationException("Instant cannot be null");
        }
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
