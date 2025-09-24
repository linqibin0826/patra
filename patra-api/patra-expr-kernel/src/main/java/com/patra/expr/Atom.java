package com.patra.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Leaf expression describing a field-level constraint.
 */
public record Atom(String fieldKey, Operator operator, Value value) implements Expr {

    public Atom {
        Objects.requireNonNull(fieldKey, "fieldKey");
        if (fieldKey.isBlank()) {
            throw new IllegalArgumentException("fieldKey must not be blank");
        }
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        operator.verifyValueCompatibility(value);
    }

    @Override
    public <R> R accept(ExprVisitor<R> visitor) {
        return visitor.visitAtom(this);
    }

    /**
     * Supported field operators.
     */
    public enum Operator {
        TERM(TermValue.class),
        IN(InValues.class),
        RANGE(RangeValue.class),
        EXISTS(ExistsFlag.class),
        TOKEN(TokenValue.class);

        private final Class<? extends Value> supportedType;

        Operator(Class<? extends Value> supportedType) {
            this.supportedType = supportedType;
        }

        void verifyValueCompatibility(Value value) {
            if (!supportedType.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Operator " + this + " does not support value type " + value.getClass().getSimpleName());
            }
        }
    }

    /**
     * Marker parent for all value variants.
     */
    public sealed interface Value permits TermValue, InValues, RangeValue, ExistsFlag, TokenValue {
    }

    /**
     * TEXT based value for TERM operations.
     */
    public record TermValue(String text, TextMatch match, CaseSensitivity caseSensitivity) implements Value {
        public TermValue {
            Objects.requireNonNull(match, "match");
            Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        }

        public TermValue(String text, TextMatch match) {
            this(text, match, CaseSensitivity.INSENSITIVE);
        }
    }

    /**
     * Collection of discrete string values used for IN operations.
     */
    public record InValues(List<String> values, CaseSensitivity caseSensitivity) implements Value {
        public InValues {
            Objects.requireNonNull(values, "values");
            if (values.isEmpty()) {
                throw new IllegalArgumentException("IN values must contain at least one item");
            }
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("IN values cannot contain null items");
            }
            values = List.copyOf(values);
            Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        }

        public InValues(List<String> values) {
            this(values, CaseSensitivity.INSENSITIVE);
        }
    }

    /**
     * Common contract for range based values.
     */
    public sealed interface RangeValue extends Value permits DateRange, DateTimeRange, NumberRange {
        Boundary fromBoundary();

        Boundary toBoundary();

        enum Boundary {
            OPEN,
            CLOSED
        }
    }

    public record DateRange(LocalDate from,
                            LocalDate to,
                            Boundary fromBoundary,
                            Boundary toBoundary) implements RangeValue {
        public DateRange {
            Objects.requireNonNull(fromBoundary, "fromBoundary");
            Objects.requireNonNull(toBoundary, "toBoundary");
        }

        public DateRange(LocalDate from, LocalDate to) {
            this(from, to, Boundary.CLOSED, Boundary.CLOSED);
        }
    }

    public record DateTimeRange(Instant from,
                                Instant to,
                                Boundary fromBoundary,
                                Boundary toBoundary) implements RangeValue {
        public DateTimeRange {
            Objects.requireNonNull(fromBoundary, "fromBoundary");
            Objects.requireNonNull(toBoundary, "toBoundary");
        }

        public DateTimeRange(Instant from, Instant to) {
            this(from, to, Boundary.CLOSED, Boundary.CLOSED);
        }
    }

    public record NumberRange(BigDecimal from,
                              BigDecimal to,
                              Boundary fromBoundary,
                              Boundary toBoundary) implements RangeValue {
        public NumberRange {
            Objects.requireNonNull(fromBoundary, "fromBoundary");
            Objects.requireNonNull(toBoundary, "toBoundary");
        }

        public NumberRange(BigDecimal from, BigDecimal to) {
            this(from, to, Boundary.CLOSED, Boundary.CLOSED);
        }
    }

    /**
     * EXISTS operation – simply indicates presence/absence.
     */
    public record ExistsFlag(boolean shouldExist) implements Value {
    }

    /**
     * TOKEN operation – platform specific token semantics.
     */
    public record TokenValue(String tokenType, String tokenValue) implements Value {
        public TokenValue {
            Objects.requireNonNull(tokenType, "tokenType");
            Objects.requireNonNull(tokenValue, "tokenValue");
        }
    }
}
