package com.patra.starter.expr.compiler.normalize;

import com.patra.expr.And;
import com.patra.expr.Atom;
import com.patra.expr.CaseSensitivity;
import com.patra.expr.Const;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.Not;
import com.patra.expr.Or;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class DefaultExprNormalizer implements ExprNormalizer {

    @Override
    public Expr normalize(Expr expression, ProvenanceSnapshot snapshot, boolean strictMode) {
        Objects.requireNonNull(expression, "expression");
        return normalizeNode(expression);
    }

    private Expr normalizeNode(Expr expr) {
        if (expr instanceof Atom atom) {
            return normalizeAtom(atom);
        }
        if (expr instanceof And andExpr) {
            return normalizeAnd(andExpr);
        }
        if (expr instanceof Or orExpr) {
            return normalizeOr(orExpr);
        }
        if (expr instanceof Not notExpr) {
            return normalizeNot(notExpr);
        }
        return expr;
    }

    private Expr normalizeAtom(Atom atom) {
        return switch (atom.operator()) {
            case TERM -> normalizeTerm(atom);
            case IN -> normalizeIn(atom);
            default -> atom;
        };
    }

    private Expr normalizeTerm(Atom atom) {
        Atom.TermValue term = (Atom.TermValue) atom.value();
        String text = term.text();
        String trimmed = text == null ? null : text.trim();
        if (Objects.equals(text, trimmed)) {
            return atom;
        }
        return Exprs.term(atom.fieldKey(), trimmed, term.match(), term.caseSensitivity());
    }

    private Expr normalizeIn(Atom atom) {
        Atom.InValues values = (Atom.InValues) atom.value();
        List<String> raw = values.values();
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String v : raw) {
            if (v == null) {
                continue;
            }
            String trimmed = v.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String key = values.caseSensitivity().isSensitive() ? trimmed : trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.isEmpty()) {
            return Const.FALSE;
        }
        if (cleaned.size() == 1) {
            String value = cleaned.getFirst();
            return Exprs.term(atom.fieldKey(), value, TextMatch.PHRASE, values.caseSensitivity());
        }
        return Exprs.in(atom.fieldKey(), cleaned, values.caseSensitivity());
    }

    private Expr normalizeAnd(And andExpr) {
        List<Expr> normalized = new ArrayList<>();
        boolean hasFalse = false;
        for (Expr child : andExpr.children()) {
            Expr n = normalizeNode(child);
            if (n == Const.FALSE) {
                hasFalse = true;
                break;
            }
            if (n != Const.TRUE) {
                if (n instanceof And nested) {
                    normalized.addAll(nested.children());
                } else {
                    normalized.add(n);
                }
            }
        }
        if (hasFalse) {
            return Const.FALSE;
        }
        if (normalized.isEmpty()) {
            return Const.TRUE;
        }
        normalized = dedupe(normalized);
        if (normalized.size() == 1) {
            return normalized.getFirst();
        }
        return Exprs.and(normalized);
    }

    private Expr normalizeOr(Or orExpr) {
        List<Expr> normalized = new ArrayList<>();
        boolean hasTrue = false;
        for (Expr child : orExpr.children()) {
            Expr n = normalizeNode(child);
            if (n == Const.TRUE) {
                hasTrue = true;
                break;
            }
            if (n != Const.FALSE) {
                if (n instanceof Or nested) {
                    normalized.addAll(nested.children());
                } else {
                    normalized.add(n);
                }
            }
        }
        if (hasTrue) {
            return Const.TRUE;
        }
        if (normalized.isEmpty()) {
            return Const.FALSE;
        }
        normalized = dedupe(normalized);
        if (normalized.size() == 1) {
            return normalized.getFirst();
        }
        return Exprs.or(normalized);
    }

    private Expr normalizeNot(Not notExpr) {
        Expr child = normalizeNode(notExpr.child());
        if (child instanceof Const constant) {
            return constant == Const.TRUE ? Const.FALSE : Const.TRUE;
        }
        if (child instanceof Not nested) {
            return normalizeNode(nested.child());
        }
        return Exprs.not(child);
    }

    private List<Expr> dedupe(List<Expr> expressions) {
        Set<Expr> ordered = new LinkedHashSet<>(expressions);
        return new ArrayList<>(ordered);
    }
}
