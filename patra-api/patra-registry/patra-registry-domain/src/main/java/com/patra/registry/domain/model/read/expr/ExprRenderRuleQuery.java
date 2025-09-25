package com.patra.registry.domain.model.read.expr;

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
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("Field key cannot be blank");
        }
        if (opCode == null || opCode.isBlank()) {
            throw new IllegalArgumentException("Operation code cannot be blank");
        }
        if (emitTypeCode == null || emitTypeCode.isBlank()) {
            throw new IllegalArgumentException("Emit type code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        fieldKey = fieldKey.trim();
        opCode = opCode.trim();
        matchTypeCode = matchTypeCode != null ? matchTypeCode.trim() : null;
        valueTypeCode = valueTypeCode != null ? valueTypeCode.trim() : null;
        emitTypeCode = emitTypeCode.trim();
    }
}
