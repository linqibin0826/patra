package com.patra.expr;

import com.patra.expr.Atom.DateRange;
import com.patra.expr.Atom.DateTimeRange;
import com.patra.expr.Atom.ExistsFlag;
import com.patra.expr.Atom.InValues;
import com.patra.expr.Atom.NumberRange;
import com.patra.expr.Atom.Operator;
import com.patra.expr.Atom.RangeValue.Boundary;
import com.patra.expr.Atom.TermValue;
import com.patra.expr.Atom.TokenValue;
import com.patra.expr.json.ExprJsonCodec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Static factory for expression nodes.
 *
 * <p>Provides convenient methods for constructing expression trees without directly instantiating
 * record classes. All methods perform validation and return immutable expression nodes.
 */
public final class Exprs {
  private Exprs() {}

  /** Returns a constant TRUE expression. */
  public static Expr constTrue() {
    return Const.TRUE;
  }

  /** Returns a constant FALSE expression. */
  public static Expr constFalse() {
    return Const.FALSE;
  }

  /**
   * Creates an AND expression combining multiple child expressions.
   *
   * @param children list of expressions to combine with AND logic
   * @return AND expression
   */
  public static Expr and(List<Expr> children) {
    return new And(children);
  }

  /**
   * Creates an OR expression combining multiple child expressions.
   *
   * @param children list of expressions to combine with OR logic
   * @return OR expression
   */
  public static Expr or(List<Expr> children) {
    return new Or(children);
  }

  /**
   * Creates a NOT expression negating a child expression.
   *
   * @param child expression to negate
   * @return NOT expression
   */
  public static Expr not(Expr child) {
    return new Not(child);
  }

  /**
   * Creates a TERM expression with case-insensitive matching.
   *
   * @param field field name to search
   * @param value text value to match
   * @param match matching strategy
   * @return TERM expression
   */
  public static Expr term(String field, String value, TextMatch match) {
    return term(field, value, match, CaseSensitivity.INSENSITIVE);
  }

  /**
   * Creates a TERM expression with configurable case sensitivity.
   *
   * @param field field name to search
   * @param value text value to match
   * @param match matching strategy
   * @param caseSensitive true for case-sensitive, false for case-insensitive
   * @return TERM expression
   */
  public static Expr term(String field, String value, TextMatch match, boolean caseSensitive) {
    return term(field, value, match, CaseSensitivity.of(caseSensitive));
  }

  /**
   * Creates a TERM expression with explicit case sensitivity control.
   *
   * @param field field name to search
   * @param value text value to match
   * @param match matching strategy
   * @param caseSensitivity case sensitivity behavior
   * @return TERM expression
   */
  public static Expr term(
      String field, String value, TextMatch match, CaseSensitivity caseSensitivity) {
    return new Atom(field, Operator.TERM, new TermValue(value, match, caseSensitivity));
  }

  /**
   * Creates an IN expression with case-insensitive matching.
   *
   * @param field field name to search
   * @param values list of values to match against
   * @return IN expression
   */
  public static Expr in(String field, List<String> values) {
    return in(field, values, CaseSensitivity.INSENSITIVE);
  }

  /**
   * Creates an IN expression with configurable case sensitivity.
   *
   * @param field field name to search
   * @param values list of values to match against
   * @param caseSensitive true for case-sensitive, false for case-insensitive
   * @return IN expression
   */
  public static Expr in(String field, List<String> values, boolean caseSensitive) {
    return in(field, values, CaseSensitivity.of(caseSensitive));
  }

  /**
   * Creates an IN expression with explicit case sensitivity control.
   *
   * @param field field name to search
   * @param values list of values to match against
   * @param caseSensitivity case sensitivity behavior
   * @return IN expression
   */
  public static Expr in(String field, List<String> values, CaseSensitivity caseSensitivity) {
    return new Atom(field, Operator.IN, new InValues(values, caseSensitivity));
  }

  /**
   * Creates a closed date range expression.
   *
   * @param field field name to search
   * @param from lower bound date (inclusive)
   * @param to upper bound date (inclusive)
   * @return RANGE expression for dates
   */
  public static Expr rangeDate(String field, LocalDate from, LocalDate to) {
    return new Atom(field, Operator.RANGE, new DateRange(from, to));
  }

  /**
   * Creates a date range expression with configurable boundary inclusion.
   *
   * @param field field name to search
   * @param from lower bound date
   * @param to upper bound date
   * @param includeFrom true to include lower bound, false to exclude
   * @param includeTo true to include upper bound, false to exclude
   * @return RANGE expression for dates
   */
  public static Expr rangeDate(
      String field, LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo) {
    return new Atom(
        field,
        Operator.RANGE,
        new DateRange(from, to, toBoundary(includeFrom), toBoundary(includeTo)));
  }

  /**
   * Creates a closed datetime range expression.
   *
   * @param field field name to search
   * @param from lower bound instant (inclusive)
   * @param to upper bound instant (inclusive)
   * @return RANGE expression for datetimes
   */
  public static Expr rangeDateTime(String field, Instant from, Instant to) {
    return new Atom(field, Operator.RANGE, new DateTimeRange(from, to));
  }

  /**
   * Creates a datetime range expression with configurable boundary inclusion.
   *
   * @param field field name to search
   * @param from lower bound instant
   * @param to upper bound instant
   * @param includeFrom true to include lower bound, false to exclude
   * @param includeTo true to include upper bound, false to exclude
   * @return RANGE expression for datetimes
   */
  public static Expr rangeDateTime(
      String field, Instant from, Instant to, boolean includeFrom, boolean includeTo) {
    return new Atom(
        field,
        Operator.RANGE,
        new DateTimeRange(from, to, toBoundary(includeFrom), toBoundary(includeTo)));
  }

  /**
   * Creates a closed number range expression.
   *
   * @param field field name to search
   * @param from lower bound number (inclusive)
   * @param to upper bound number (inclusive)
   * @return RANGE expression for numbers
   */
  public static Expr rangeNumber(String field, BigDecimal from, BigDecimal to) {
    return new Atom(field, Operator.RANGE, new NumberRange(from, to));
  }

  /**
   * Creates a number range expression with configurable boundary inclusion.
   *
   * @param field field name to search
   * @param from lower bound number
   * @param to upper bound number
   * @param includeFrom true to include lower bound, false to exclude
   * @param includeTo true to include upper bound, false to exclude
   * @return RANGE expression for numbers
   */
  public static Expr rangeNumber(
      String field, BigDecimal from, BigDecimal to, boolean includeFrom, boolean includeTo) {
    return new Atom(
        field,
        Operator.RANGE,
        new NumberRange(from, to, toBoundary(includeFrom), toBoundary(includeTo)));
  }

  /**
   * Creates an EXISTS expression checking field presence or absence.
   *
   * @param field field name to check
   * @param shouldExist true to check existence, false to check absence
   * @return EXISTS expression
   */
  public static Expr exists(String field, boolean shouldExist) {
    return new Atom(field, Operator.EXISTS, new ExistsFlag(shouldExist));
  }

  /**
   * Creates a TOKEN expression using the token type as both field key and token type.
   *
   * <p>This is a convenience method that matches the previous API behavior.
   *
   * @param tokenType the token type (used as both field and type)
   * @param tokenValue the token value
   * @return TOKEN expression
   */
  public static Expr token(String tokenType, String tokenValue) {
    return token(tokenType, tokenType, tokenValue);
  }

  /**
   * Creates a TOKEN expression with separate field and token type.
   *
   * @param field field name to search
   * @param tokenType the type of token (e.g., "MeSH", "GeneSymbol")
   * @param tokenValue the token value
   * @return TOKEN expression
   */
  public static Expr token(String field, String tokenType, String tokenValue) {
    return new Atom(field, Operator.TOKEN, new TokenValue(tokenType, tokenValue));
  }

  /**
   * Serializes an expression tree to JSON.
   *
   * @param expr expression to serialize
   * @return JSON string representation
   */
  public static String toJson(Expr expr) {
    return ExprJsonCodec.toJson(expr);
  }

  /**
   * Deserializes JSON into an expression tree.
   *
   * @param json JSON string to parse
   * @return expression tree
   */
  public static Expr fromJson(String json) {
    return ExprJsonCodec.fromJson(json);
  }

  private static Boundary toBoundary(boolean inclusive) {
    return inclusive ? Boundary.CLOSED : Boundary.OPEN;
  }
}
