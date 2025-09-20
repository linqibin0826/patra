package com.patra.registry.api.rpc.dto.expr;

/**
 * Expr 字段响应 DTO。
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
