package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.util.Objects;

/**
 * Domain value object for unified expression field dictionary (reg_expr_field_dict).
 *
 * <p>Describes canonical atom fields and core attributes used by expression
 * modeling, capability declaration, and render rule selection.</p>
 *
 * @author linqibin
 * @since 0.1.0
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
        DomainValidationException.positive(id, "Expr field id");
        String keyTrimmed = DomainValidationException.notBlank(fieldKey, "Expr field key");
        String dtTrimmed = DomainValidationException.notBlank(dataTypeCode, "Expr field data type code");
        String cardinalityTrimmed = DomainValidationException.notBlank(cardinalityCode, "Expr field cardinality code");

        this.id = id; // already validated
        this.fieldKey = keyTrimmed;
        this.displayName = displayName != null ? displayName.trim() : "";
        this.description = description != null ? description.trim() : "";
        this.dataTypeCode = dtTrimmed;
        this.cardinalityCode = cardinalityTrimmed;
        this.exposable = exposable;
        this.dateField = dateField;
    }

    /** Whether the field is exposable to clients. */
    public boolean isExposable() {
        return exposable;
    }

    /** Whether the field should be treated as date-like for rendering/validation branches. */
    public boolean isDateField() {
        return dateField;
    }

    /** Equality is based only on fieldKey (stable business key). */
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
