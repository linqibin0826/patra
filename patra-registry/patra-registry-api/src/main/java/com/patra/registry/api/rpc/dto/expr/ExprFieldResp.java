package com.patra.registry.api.rpc.dto.expr;

/**
 * Response DTO describing a single expression field definition exposed to RPC clients.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>fieldKey - unique field identifier aligned with domain vocabulary</li>
 *   <li>displayName - human-readable field label for UIs and logs</li>
 *   <li>description - optional field documentation presented to consumers</li>
 *   <li>dataTypeCode - data type discriminator (STRING/NUMBER/DATE/etc.)</li>
 *   <li>cardinalityCode - cardinality discriminator (SINGLE/MULTI)</li>
 *   <li>exposable - whether the field is exposed to clients</li>
 *   <li>dateField - whether the field represents date semantics for downstream parsing</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprFieldResp(
        String fieldKey,
        String displayName,
        String description,
        String dataTypeCode,
        String cardinalityCode,
        boolean exposable,
        boolean dateField
) {
}
