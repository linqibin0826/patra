package com.patra.registry.api.dto.expr;

import java.time.Instant;

/**
 * Render rules for expression output templating.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>provenanceId - internal provenance identifier owning the rule
 *   <li>operationType - operation discriminator the rule applies to
 *   <li>fieldKey - expression field key the render rule targets
 *   <li>opCode - logical operator used when evaluating the field
 *   <li>matchTypeCode - match type discriminator for render evaluation
 *   <li>negated - whether the rendered expression is negated
 *   <li>valueTypeCode - value type discriminator for substitution
 *   <li>emitTypeCode - emit strategy for templated output
 *   <li>template - top-level template fragment
 *   <li>itemTemplate - template fragment for list items
 *   <li>joiner - join token applied between rendered fragments
 *   <li>wrapGroup - whether to wrap rendered fragment with grouping tokens
 *   <li>paramsJson - serialized parameter metadata
 *   <li>functionCode - optional function identifier applied during rendering
 *   <li>effectiveFrom - timestamp from which the rule becomes effective
 *   <li>effectiveTo - timestamp until which the rule remains effective
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprRenderRuleResp(
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
    Instant effectiveTo) {}
