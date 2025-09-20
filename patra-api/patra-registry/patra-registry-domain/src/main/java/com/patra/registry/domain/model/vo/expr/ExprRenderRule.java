package com.patra.registry.domain.model.vo.expr;

import java.time.Instant;

/**
 * 表 {@code reg_prov_expr_render_rule} 对应的领域值对象。
 */
public record ExprRenderRule(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
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
        String functionCode
) {
    public ExprRenderRule(Long id,
                          Long provenanceId,
                          String scopeCode,
                          String taskType,
                          String taskTypeKey,
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
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Render rule id must be positive");
        }
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
        if (matchTypeKey == null || matchTypeKey.isBlank()) {
            throw new IllegalArgumentException("Match type key cannot be blank");
        }
        if (negatedKey == null || negatedKey.isBlank()) {
            throw new IllegalArgumentException("Negated key cannot be blank");
        }
        if (valueTypeKey == null || valueTypeKey.isBlank()) {
            throw new IllegalArgumentException("Value type key cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
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

    /** 判断是否在给定时间点生效。 */
    public boolean isEffectiveAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Instant cannot be null");
        }
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
