package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.support.TemporalEntity;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_expr_render_rule}.
 *
 * <p>Defines how an expression atom (field/op/match/negation/value-type) is rendered into query
 * fragments or standard parameters.
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
    /* Normalization of {@code matchTypeCode}: {@code null} -> {@code ANY} */
    String matchTypeKey,
    /* Normalization of {@code negated}: {@code null} -> {@code ANY}, {@code true} -> {@code T}, {@code false} -> {@code F} */
    String negatedKey,
    /* Normalization of {@code valueTypeCode}: {@code null} -> {@code ANY} */
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
    String functionCode)
    implements TemporalEntity {
  public ExprRenderRule(
      Long id,
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
    validateBasicFields(id, provenanceId, fieldKey, opCode, emitTypeCode, effectiveFrom);
    validateNormalizedKeys(matchTypeKey, negatedKey, valueTypeKey);

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = DomainValidationException.trimOrNull(operationType);
    this.fieldKey = fieldKey.trim();
    this.opCode = opCode.trim();
    this.matchTypeCode = DomainValidationException.trimOrNull(matchTypeCode);
    this.negated = negated;
    this.valueTypeCode = DomainValidationException.trimOrNull(valueTypeCode);
    this.emitTypeCode = emitTypeCode.trim();
    this.matchTypeKey = matchTypeKey.trim();
    this.negatedKey = negatedKey.trim();
    this.valueTypeKey = valueTypeKey.trim();
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.template = template;
    this.itemTemplate = itemTemplate;
    this.joiner = DomainValidationException.trimOrNull(joiner);
    this.wrapGroup = wrapGroup;
    this.paramsJson = paramsJson;
    this.functionCode = DomainValidationException.trimOrNull(functionCode);
  }

  /**
   * Validates basic required fields for render rule.
   *
   * @param id rule identifier
   * @param provenanceId provenance identifier
   * @param fieldKey field key
   * @param opCode operation code
   * @param emitTypeCode emit type code
   * @param effectiveFrom effective start timestamp
   */
  private static void validateBasicFields(
      Long id,
      Long provenanceId,
      String fieldKey,
      String opCode,
      String emitTypeCode,
      Instant effectiveFrom) {
    DomainValidationException.positive(id, "Render rule id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.notBlank(fieldKey, "Field key");
    DomainValidationException.notBlank(opCode, "Operation code");
    DomainValidationException.notBlank(emitTypeCode, "Emit type code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
  }

  /**
   * Validates normalized dimension keys.
   *
   * @param matchTypeKey normalized match type key
   * @param negatedKey normalized negated key
   * @param valueTypeKey normalized value type key
   */
  private static void validateNormalizedKeys(
      String matchTypeKey, String negatedKey, String valueTypeKey) {
    DomainValidationException.notBlank(matchTypeKey, "Match type key");
    DomainValidationException.notBlank(negatedKey, "Negated key");
    DomainValidationException.notBlank(valueTypeKey, "Value type key");
  }
}
