package com.patra.expr;

import com.patra.expr.Atom.ExistsFlag;
import com.patra.expr.Atom.InValues;
import com.patra.expr.Atom.Operator;
import com.patra.expr.Atom.RangeValue.Boundary;
import com.patra.expr.Atom.DateRange;
import com.patra.expr.Atom.DateTimeRange;
import com.patra.expr.Atom.NumberRange;
import com.patra.expr.Atom.TermValue;
import com.patra.expr.Atom.TokenValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Static factory for expression nodes.
 */
public final class Exprs {
    private Exprs() {
    }

    public static Expr constTrue() {
        return Const.TRUE;
    }

    public static Expr constFalse() {
        return Const.FALSE;
    }

    public static Expr and(List<Expr> children) {
        return new And(children);
    }

    public static Expr or(List<Expr> children) {
        return new Or(children);
    }

    public static Expr not(Expr child) {
        return new Not(child);
    }

    public static Expr term(String field, String value, TextMatch match) {
        return term(field, value, match, CaseSensitivity.INSENSITIVE);
    }

    public static Expr term(String field, String value, TextMatch match, boolean caseSensitive) {
        return term(field, value, match, CaseSensitivity.of(caseSensitive));
    }

    public static Expr term(String field, String value, TextMatch match, CaseSensitivity caseSensitivity) {
        return new Atom(field, Operator.TERM, new TermValue(value, match, caseSensitivity));
    }

    public static Expr in(String field, List<String> values) {
        return in(field, values, CaseSensitivity.INSENSITIVE);
    }

    public static Expr in(String field, List<String> values, boolean caseSensitive) {
        return in(field, values, CaseSensitivity.of(caseSensitive));
    }

    public static Expr in(String field, List<String> values, CaseSensitivity caseSensitivity) {
        return new Atom(field, Operator.IN, new InValues(values, caseSensitivity));
    }

    public static Expr rangeDate(String field, LocalDate from, LocalDate to) {
        return new Atom(field, Operator.RANGE, new DateRange(from, to));
    }

    public static Expr rangeDate(String field,
                                 LocalDate from,
                                 LocalDate to,
                                 boolean includeFrom,
                                 boolean includeTo) {
        return new Atom(field, Operator.RANGE, new DateRange(
                from,
                to,
                includeFrom ? Boundary.CLOSED : Boundary.OPEN,
                includeTo ? Boundary.CLOSED : Boundary.OPEN
        ));
    }

    public static Expr rangeDateTime(String field, Instant from, Instant to) {
        return new Atom(field, Operator.RANGE, new DateTimeRange(from, to));
    }

    public static Expr rangeDateTime(String field,
                                     Instant from,
                                     Instant to,
                                     boolean includeFrom,
                                     boolean includeTo) {
        return new Atom(field, Operator.RANGE, new DateTimeRange(
                from,
                to,
                includeFrom ? Boundary.CLOSED : Boundary.OPEN,
                includeTo ? Boundary.CLOSED : Boundary.OPEN
        ));
    }

    public static Expr rangeNumber(String field, BigDecimal from, BigDecimal to) {
        return new Atom(field, Operator.RANGE, new NumberRange(from, to));
    }

    public static Expr rangeNumber(String field,
                                   BigDecimal from,
                                   BigDecimal to,
                                   boolean includeFrom,
                                   boolean includeTo) {
        return new Atom(field, Operator.RANGE, new NumberRange(
                from,
                to,
                includeFrom ? Boundary.CLOSED : Boundary.OPEN,
                includeTo ? Boundary.CLOSED : Boundary.OPEN
        ));
    }

    public static Expr exists(String field, boolean shouldExist) {
        return new Atom(field, Operator.EXISTS, new ExistsFlag(shouldExist));
    }

    /**
     * TOKEN helper that uses the provided {@code tokenType} as both the field key and the token type,
     * which matches the previous behaviour of the API.
     */
    public static Expr token(String tokenType, String tokenValue) {
        return token(tokenType, tokenType, tokenValue);
    }

    public static Expr token(String field, String tokenType, String tokenValue) {
        return new Atom(field, Operator.TOKEN, new TokenValue(tokenType, tokenValue));
    }
}
