package com.patra.starter.expr.compiler.check;

import com.patra.expr.And;
import com.patra.expr.Atom;
import com.patra.expr.CaseSensitivity;
import com.patra.expr.Const;
import com.patra.expr.Expr;
import com.patra.expr.Not;
import com.patra.expr.Or;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultCapabilityChecker implements CapabilityChecker {

    @Override
    public List<Issue> check(Expr expression, ProvenanceSnapshot snapshot, boolean strictMode) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(snapshot, "snapshot");
        List<Issue> issues = new ArrayList<>();
        visit(expression, false, snapshot, strictMode, issues);
        return issues;
    }

    private void visit(Expr node,
                       boolean underNot,
                       ProvenanceSnapshot snapshot,
                       boolean strictMode,
                       List<Issue> out) {
        if (node instanceof And andExpr) {
            andExpr.children().forEach(child -> visit(child, underNot, snapshot, strictMode, out));
            return;
        }
        if (node instanceof Or orExpr) {
            orExpr.children().forEach(child -> visit(child, underNot, snapshot, strictMode, out));
            return;
        }
        if (node instanceof Not notExpr) {
            visit(notExpr.child(), !underNot, snapshot, strictMode, out);
            return;
        }
        if (node instanceof Const) {
            return;
        }
        if (node instanceof Atom atom) {
            validateAtom(atom, underNot, snapshot, strictMode, out);
        }
    }

    private void validateAtom(Atom atom,
                              boolean underNot,
                              ProvenanceSnapshot snapshot,
                              boolean strictMode,
                              List<Issue> out) {
        String fieldKey = atom.fieldKey();
        ProvenanceSnapshot.FieldDefinition definition = snapshot.fieldDictionary().get(fieldKey);
        if (definition == null) {
            out.add(Issue.error("E-FIELD-NOT-FOUND",
                    "Field not registered in provenance",
                    Map.of("fieldKey", fieldKey)));
            return;
        }

        ProvenanceSnapshot.Capability capability = snapshot.capabilityMatrix().get(fieldKey);
        if (capability == null) {
            out.add(Issue.error("E-CAPABILITY-MISSING",
                    "No capability definition for field",
                    Map.of("fieldKey", fieldKey)));
            return;
        }

        String op = atom.operator().name();
        if (!capability.ops().contains(op)) {
            out.add(Issue.error("E-OP-NOT-ALLOWED",
                    "Operator not supported for field",
                    Map.of("fieldKey", fieldKey, "operator", op)));
            return;
        }

        if (underNot) {
            if (!capability.supportsNot()) {
                out.add(Issue.error("E-NOT-UNSUPPORTED",
                        "Negation is not supported for this field",
                        Map.of("fieldKey", fieldKey)));
            } else if (!capability.negatableOps().isEmpty() && !capability.negatableOps().contains(op)) {
                out.add(Issue.error("E-NOT-OP-UNSUPPORTED",
                        "Negation is not supported for this operator",
                        Map.of("fieldKey", fieldKey, "operator", op)));
            }
        }

        switch (atom.operator()) {
            case TERM -> validateTerm(atom, capability, out);
            case IN -> validateIn(atom, capability, out);
            case RANGE -> validateRange(atom, capability, out);
            case EXISTS -> validateExists(capability, fieldKey, out);
            case TOKEN -> validateToken(atom, capability, strictMode, out);
        }
    }

    private void validateTerm(Atom atom,
                              ProvenanceSnapshot.Capability capability,
                              List<Issue> out) {
        Atom.TermValue value = (Atom.TermValue) atom.value();
        String text = value.text();
        if ((text == null || text.isBlank()) && !capability.termAllowBlank()) {
            out.add(Issue.error("E-TERM-BLANK",
                    "TERM does not allow blank values",
                    Map.of("fieldKey", atom.fieldKey())));
            return;
        }

        if (text != null) {
            int length = text.length();
            if (capability.termMinLength() > 0 && length < capability.termMinLength()) {
                out.add(Issue.error("E-TERM-LEN-MIN",
                        "TERM shorter than minimum length",
                        Map.of("fieldKey", atom.fieldKey(), "length", length, "min", capability.termMinLength())));
            }
            if (capability.termMaxLength() > 0 && length > capability.termMaxLength()) {
                out.add(Issue.error("E-TERM-LEN-MAX",
                        "TERM exceeds maximum length",
                        Map.of("fieldKey", atom.fieldKey(), "length", length, "max", capability.termMaxLength())));
            }
            if (capability.termPattern() != null && !capability.termPattern().isBlank()) {
                if (!text.matches(capability.termPattern())) {
                    out.add(Issue.error("E-TERM-PATTERN",
                            "TERM violates pattern constraint",
                            Map.of("fieldKey", atom.fieldKey(), "pattern", capability.termPattern())));
                }
            }
        }

        CaseSensitivity cs = value.caseSensitivity();
        if (cs.isSensitive() && !capability.termCaseSensitiveAllowed()) {
            out.add(Issue.error("E-TERM-CASE-SENSITIVE",
                    "Case sensitive TERM not supported",
                    Map.of("fieldKey", atom.fieldKey())));
        }

        Set<String> matches = capability.termMatches();
        if (matches != null && !matches.isEmpty()) {
            String match = value.match().name().toUpperCase(Locale.ROOT);
            if (!matches.contains(match)) {
                out.add(Issue.error("E-TERM-MATCH-UNSUPPORTED",
                        "Match strategy not supported",
                        Map.of("fieldKey", atom.fieldKey(), "match", match)));
            }
        }
    }

    private void validateIn(Atom atom,
                            ProvenanceSnapshot.Capability capability,
                            List<Issue> out) {
        Atom.InValues values = (Atom.InValues) atom.value();
        if (values.values().isEmpty()) {
            out.add(Issue.error("E-IN-EMPTY", "IN requires at least one value", Map.of("fieldKey", atom.fieldKey())));
            return;
        }
        if (capability.inMaxSize() > 0 && values.values().size() > capability.inMaxSize()) {
            out.add(Issue.error("E-IN-SIZE",
                    "IN exceeds maximum item count",
                    Map.of("fieldKey", atom.fieldKey(), "size", values.values().size(), "max", capability.inMaxSize())));
        }
        if (values.caseSensitivity().isSensitive() && !capability.inCaseSensitiveAllowed()) {
            out.add(Issue.error("E-IN-CASE-SENSITIVE",
                    "Case sensitive IN not supported",
                    Map.of("fieldKey", atom.fieldKey())));
        }
    }

    private void validateRange(Atom atom,
                               ProvenanceSnapshot.Capability capability,
                               List<Issue> out) {
        ProvenanceSnapshot.RangeKind kind = capability.rangeKind();
        Atom.RangeValue value = (Atom.RangeValue) atom.value();
        switch (value) {
            case Atom.DateRange dr -> {
                if (kind != ProvenanceSnapshot.RangeKind.DATE) {
                    complainRangeKind(atom, kind, "DATE", out);
                }
                validateDateBounds(atom.fieldKey(), dr.from(), dr.to(), capability.dateMin(), capability.dateMax(), out);
            }
            case Atom.DateTimeRange dtr -> {
                if (kind != ProvenanceSnapshot.RangeKind.DATETIME) {
                    complainRangeKind(atom, kind, "DATETIME", out);
                }
                if (dtr.from() == null && dtr.to() == null) {
                    out.add(Issue.error("E-RANGE-OPEN",
                            "Datetime range must specify at least one boundary",
                            Map.of("fieldKey", atom.fieldKey())));
                }
            }
            case Atom.NumberRange nr -> {
                if (kind != ProvenanceSnapshot.RangeKind.NUMBER) {
                    complainRangeKind(atom, kind, "NUMBER", out);
                }
                if (nr.from() == null && nr.to() == null) {
                    out.add(Issue.error("E-RANGE-OPEN",
                            "Number range must specify at least one boundary",
                            Map.of("fieldKey", atom.fieldKey())));
                }
            }
        }
    }

    private void validateDateBounds(String fieldKey,
                                    LocalDate from,
                                    LocalDate to,
                                    LocalDate min,
                                    LocalDate max,
                                    List<Issue> out) {
        if (from == null && to == null) {
            out.add(Issue.error("E-RANGE-OPEN",
                    "Date range must specify at least one boundary",
                    Map.of("fieldKey", fieldKey)));
            return;
        }
        if (from != null && min != null && from.isBefore(min)) {
            out.add(Issue.error("E-DATE-MIN",
                    "Date lower bound before capability minimum",
                    Map.of("fieldKey", fieldKey, "from", from, "min", min)));
        }
        if (to != null && max != null && to.isAfter(max)) {
            out.add(Issue.error("E-DATE-MAX",
                    "Date upper bound after capability maximum",
                    Map.of("fieldKey", fieldKey, "to", to, "max", max)));
        }
    }

    private void complainRangeKind(Atom atom,
                                   ProvenanceSnapshot.RangeKind actual,
                                   String expected,
                                   List<Issue> out) {
        out.add(Issue.error("E-RANGE-KIND",
                "Range kind mismatch",
                Map.of("fieldKey", atom.fieldKey(), "expected", expected, "actual", actual)));
    }

    private void validateExists(ProvenanceSnapshot.Capability capability,
                                 String fieldKey,
                                 List<Issue> out) {
        if (!capability.existsSupported()) {
            out.add(Issue.error("E-EXISTS-UNSUPPORTED",
                    "EXISTS operator is not supported",
                    Map.of("fieldKey", fieldKey)));
        }
    }

    private void validateToken(Atom atom,
                                ProvenanceSnapshot.Capability capability,
                                boolean strictMode,
                                List<Issue> out) {
        Atom.TokenValue token = (Atom.TokenValue) atom.value();
        if (token.tokenValue() == null || token.tokenValue().isBlank()) {
            if (strictMode) {
                out.add(Issue.error("E-TOKEN-BLANK",
                        "Token value must not be blank in strict mode",
                        Map.of("fieldKey", atom.fieldKey())));
            }
        }

        Set<String> kinds = capability.tokenKinds();
        if (kinds != null && !kinds.isEmpty()) {
            String type = token.tokenType() == null ? "" : token.tokenType().toLowerCase(Locale.ROOT);
            if (!kinds.contains(type)) {
                out.add(Issue.error("E-TOKEN-KIND",
                        "Token type not supported",
                        Map.of("fieldKey", atom.fieldKey(), "tokenType", token.tokenType())));
            }
        }

        if (capability.tokenValuePattern() != null && !capability.tokenValuePattern().isBlank()) {
            String value = token.tokenValue();
            if (value != null && !value.matches(capability.tokenValuePattern())) {
                out.add(Issue.error("E-TOKEN-PATTERN",
                        "Token value violates pattern",
                        Map.of("fieldKey", atom.fieldKey(), "pattern", capability.tokenValuePattern())));
            }
        }
    }
}
