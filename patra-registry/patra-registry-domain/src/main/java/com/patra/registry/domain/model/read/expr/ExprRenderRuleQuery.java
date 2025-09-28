package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * 渲染规则查询视图。
 */
public record ExprRenderRuleQuery(
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
    public ExprRenderRuleQuery {
        DomainValidationException.positive(provenanceId, "Provenance id");
        scopeCode = DomainValidationException.notBlank(scopeCode, "Scope code");
        fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
        opCode = DomainValidationException.notBlank(opCode, "Operation code");
        emitTypeCode = DomainValidationException.notBlank(emitTypeCode, "Emit type code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");
        taskType = taskType != null ? taskType.trim() : null;
        matchTypeCode = matchTypeCode != null ? matchTypeCode.trim() : null;
        valueTypeCode = valueTypeCode != null ? valueTypeCode.trim() : null;
    }
}
