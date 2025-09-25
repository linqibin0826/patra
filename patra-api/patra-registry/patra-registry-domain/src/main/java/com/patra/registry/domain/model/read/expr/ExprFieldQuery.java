package com.patra.registry.domain.model.read.expr;

/**
 * Expr 统一字段的查询视图。
 */
public record ExprFieldQuery(
        String fieldKey,
        String displayName,
        String description,
        String dataTypeCode,
        String cardinalityCode,
        boolean exposable,
        boolean dateField
) {
    public ExprFieldQuery {
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("Field key cannot be blank");
        }
        if (dataTypeCode == null || dataTypeCode.isBlank()) {
            throw new IllegalArgumentException("Data type code cannot be blank");
        }
        if (cardinalityCode == null || cardinalityCode.isBlank()) {
            throw new IllegalArgumentException("Cardinality code cannot be blank");
        }
        fieldKey = fieldKey.trim();
        displayName = displayName != null ? displayName.trim() : "";
        description = description != null ? description.trim() : "";
        dataTypeCode = dataTypeCode.trim();
        cardinalityCode = cardinalityCode.trim();
    }
}
