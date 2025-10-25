package com.patra.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Leaf expression describing a field-level constraint.
 *
 * <p>Atoms represent the basic building blocks of queries, combining a field name with an operator
 * and a value. The operator determines which value type is allowed.
 *
 * @param fieldKey the field name to query
 * @param operator the operation to perform
 * @param value the value to match against
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
   *
   * <p>Each operator is associated with a specific value type that it accepts.
   */
  public enum Operator {
    /** Text-based term matching. */
    TERM(TermValue.class),

    /** Discrete value set matching. */
    IN(InValues.class),

    /** Range-based matching for dates, times, and numbers. */
    RANGE(RangeValue.class),

    /** Field existence check. */
    EXISTS(ExistsFlag.class),

    /** Platform-specific token matching. */
    TOKEN(TokenValue.class);

    private final Class<? extends Value> supportedType;

    Operator(Class<? extends Value> supportedType) {
      this.supportedType = supportedType;
    }

    void verifyValueCompatibility(Value value) {
      if (!supportedType.isInstance(value)) {
        throw new IllegalArgumentException(
            String.format(
                "Operator %s requires value type %s but received %s",
                this, supportedType.getSimpleName(), value.getClass().getSimpleName()));
      }
    }
  }

  /**
   * Marker interface for all value variants.
   *
   * <p>Sealed to ensure type safety and exhaustive pattern matching.
   */
  public sealed interface Value permits TermValue, InValues, RangeValue, ExistsFlag, TokenValue {}

  /**
   * Text-based value for TERM operations.
   *
   * @param text the text to match
   * @param match the matching strategy
   * @param caseSensitivity case sensitivity behavior
   */
  public record TermValue(String text, TextMatch match, CaseSensitivity caseSensitivity)
      implements Value {
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
   *
   * @param values non-empty list of values to match against
   * @param caseSensitivity case sensitivity behavior
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
   * Common contract for range-based values.
   *
   * <p>Supports date, datetime, and number ranges with configurable boundary inclusion.
   */
  public sealed interface RangeValue extends Value permits DateRange, DateTimeRange, NumberRange {
    /** Returns the lower boundary inclusion type. */
    Boundary fromBoundary();

    /** Returns the upper boundary inclusion type. */
    Boundary toBoundary();

    /** Boundary inclusion type for range values. */
    enum Boundary {
      /** Excludes the boundary value from the range. */
      OPEN,

      /** Includes the boundary value in the range. */
      CLOSED
    }
  }

  /**
   * Date range value.
   *
   * @param from lower bound date
   * @param to upper bound date
   * @param fromBoundary lower boundary inclusion type
   * @param toBoundary upper boundary inclusion type
   */
  public record DateRange(LocalDate from, LocalDate to, Boundary fromBoundary, Boundary toBoundary)
      implements RangeValue {
    public DateRange {
      Objects.requireNonNull(fromBoundary, "fromBoundary");
      Objects.requireNonNull(toBoundary, "toBoundary");
    }

    public DateRange(LocalDate from, LocalDate to) {
      this(from, to, Boundary.CLOSED, Boundary.CLOSED);
    }
  }

  /**
   * DateTime range value.
   *
   * @param from lower bound instant
   * @param to upper bound instant
   * @param fromBoundary lower boundary inclusion type
   * @param toBoundary upper boundary inclusion type
   */
  public record DateTimeRange(Instant from, Instant to, Boundary fromBoundary, Boundary toBoundary)
      implements RangeValue {
    public DateTimeRange {
      Objects.requireNonNull(fromBoundary, "fromBoundary");
      Objects.requireNonNull(toBoundary, "toBoundary");
    }

    public DateTimeRange(Instant from, Instant to) {
      this(from, to, Boundary.CLOSED, Boundary.CLOSED);
    }
  }

  /**
   * Number range value.
   *
   * @param from lower bound number
   * @param to upper bound number
   * @param fromBoundary lower boundary inclusion type
   * @param toBoundary upper boundary inclusion type
   */
  public record NumberRange(
      BigDecimal from, BigDecimal to, Boundary fromBoundary, Boundary toBoundary)
      implements RangeValue {
    public NumberRange {
      Objects.requireNonNull(fromBoundary, "fromBoundary");
      Objects.requireNonNull(toBoundary, "toBoundary");
    }

    public NumberRange(BigDecimal from, BigDecimal to) {
      this(from, to, Boundary.CLOSED, Boundary.CLOSED);
    }
  }

  /**
   * EXISTS operation value indicating field presence or absence.
   *
   * @param shouldExist true to check field exists, false to check field is absent
   */
  public record ExistsFlag(boolean shouldExist) implements Value {}

  /**
   * TOKEN operation value for platform-specific token semantics.
   *
   * @param tokenType the type of token (e.g., "MeSH", "GeneSymbol")
   * @param tokenValue the token identifier value
   */
  public record TokenValue(String tokenType, String tokenValue) implements Value {
    public TokenValue {
      Objects.requireNonNull(tokenType, "tokenType");
      Objects.requireNonNull(tokenValue, "tokenValue");
    }
  }
}
