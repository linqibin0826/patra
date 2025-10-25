package com.patra.registry.api.dto.expr;

/**
 * Expression field definition exposed to RPC clients.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>fieldKey - unique field identifier aligned with domain vocabulary
 *   <li>displayName - human-readable field label for UIs and logs
 *   <li>description - optional field documentation presented to consumers
 *   <li>dataTypeCode - data type discriminator (STRING/NUMBER/DATE/etc.)
 *   <li>cardinalityCode - cardinality discriminator (SINGLE/MULTI)
 *   <li>exposable - whether the field is exposed to clients
 *   <li>dateField - whether the field represents date semantics for downstream parsing
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
    boolean dateField) {}
