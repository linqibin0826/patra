package com.patra.registry.domain.model.vo.expr;

import java.util.Objects;

/**
 * Expr 统一字段字典（reg_expr_field_dict）的领域值对象。
 *
 * <p>该对象描述了统一查询语义中的原子字段及其核心属性，
 * 用于表达式建模、能力声明与渲染规则的上游依赖。</p>
 */
public record ExprField(
        Long id,
        String fieldKey,
        String displayName,
        String description,
        String dataTypeCode,
        String cardinalityCode,
        boolean exposable,
        boolean dateField
) {
    public ExprField(Long id,
                     String fieldKey,
                     String displayName,
                     String description,
                     String dataTypeCode,
                     String cardinalityCode,
                     boolean exposable,
                     boolean dateField) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Expr field id must be positive");
        }
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("Expr field key cannot be null or blank");
        }
        if (dataTypeCode == null || dataTypeCode.isBlank()) {
            throw new IllegalArgumentException("Expr field data type code cannot be null or blank");
        }
        if (cardinalityCode == null || cardinalityCode.isBlank()) {
            throw new IllegalArgumentException("Expr field cardinality code cannot be null or blank");
        }

        this.id = id;
        this.fieldKey = fieldKey.trim();
        this.displayName = displayName != null ? displayName.trim() : "";
        this.description = description != null ? description.trim() : "";
        this.dataTypeCode = dataTypeCode.trim();
        this.cardinalityCode = cardinalityCode.trim();
        this.exposable = exposable;
        this.dateField = dateField;
    }

    /** 字段是否对外可暴露。 */
    public boolean isExposable() {
        return exposable;
    }

    /** 是否为日期字段（便于快速判断渲染与校验策略）。 */
    public boolean isDateField() {
        return dateField;
    }

    /** 判等只关心 fieldKey（稳定业务键）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExprField other)) {
            return false;
        }
        return Objects.equals(fieldKey, other.fieldKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldKey);
    }
}
