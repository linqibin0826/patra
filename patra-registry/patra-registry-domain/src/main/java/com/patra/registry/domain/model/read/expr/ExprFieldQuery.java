package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;

/**
 * Query view for unified expression fields.
 *
 * <p>Read-optimized projection for querying expression field definitions from the dictionary.
 *
 * @author linqibin
 * @since 0.1.0
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
