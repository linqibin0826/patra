package com.patra.registry.api.rpc.dto.expr;

import java.time.Instant;

/**
 * 渲染规则响应 DTO。
 */
public record ExprRenderRuleResp(
        Long provenanceId,
        String scopeCode,
        String taskType,
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
