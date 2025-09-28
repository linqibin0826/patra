package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;

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
        fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
        dataTypeCode = DomainValidationException.notBlank(dataTypeCode, "Data type code");
        cardinalityCode = DomainValidationException.notBlank(cardinalityCode, "Cardinality code");
        displayName = displayName != null ? displayName.trim() : "";
        description = description != null ? description.trim() : "";
    }
}
