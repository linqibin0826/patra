package com.patra.registry.api.rpc.dto.expr;

import java.time.Instant;

/**
 * Response DTO describing render rules for expression output templating.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>provenanceId - internal provenance identifier owning the rule</li>
 *   <li>operationType - operation discriminator the rule applies to</li>
 *   <li>fieldKey - expression field key the render rule targets</li>
 *   <li>opCode - logical operator used when evaluating the field</li>
 *   <li>matchTypeCode - match type discriminator for render evaluation</li>
 *   <li>negated - whether the rendered expression is negated</li>
 *   <li>valueTypeCode - value type discriminator for substitution</li>
 *   <li>emitTypeCode - emit strategy for templated output</li>
 *   <li>template - top-level template fragment</li>
 *   <li>itemTemplate - template fragment for list items</li>
 *   <li>joiner - join token applied between rendered fragments</li>
 *   <li>wrapGroup - whether to wrap rendered fragment with grouping tokens</li>
 *   <li>paramsJson - serialized parameter metadata</li>
 *   <li>functionCode - optional function identifier applied during rendering</li>
 *   <li>effectiveFrom - timestamp from which the rule becomes effective</li>
 *   <li>effectiveTo - timestamp until which the rule remains effective</li>
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
        Instant effectiveTo
) {
}
