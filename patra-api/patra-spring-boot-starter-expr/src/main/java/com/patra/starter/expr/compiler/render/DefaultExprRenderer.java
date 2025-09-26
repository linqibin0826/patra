package com.patra.starter.expr.compiler.render;

import com.patra.expr.And;
import com.patra.expr.Atom;
import com.patra.expr.Const;
import com.patra.expr.Expr;
import com.patra.expr.Not;
import com.patra.expr.Or;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultExprRenderer implements ExprRenderer {

    @Override
    public RenderOutcome render(Expr expression, ProvenanceSnapshot snapshot, boolean traceEnabled) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(snapshot, "snapshot");

        List<String> queryFragments = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();
        List<Issue> warnings = new ArrayList<>();
        List<RenderTrace.Hit> hits = traceEnabled ? new ArrayList<>() : null;

        renderNode(expression, snapshot, queryFragments, params, warnings, hits);

        String query = String.join(" AND ", queryFragments);
        RenderTrace trace = traceEnabled ? new RenderTrace(hits) : null;
        return new RenderOutcome(query, params, warnings, trace);
    }

    private void renderNode(Expr node,
                            ProvenanceSnapshot snapshot,
                            List<String> fragments,
                            Map<String, String> params,
                            List<Issue> warnings,
                            List<RenderTrace.Hit> hits) {
        if (node instanceof And andExpr) {
            andExpr.children().forEach(child -> renderNode(child, snapshot, fragments, params, warnings, hits));
            return;
        }
        if (node instanceof Or) {
            warnings.add(Issue.warn("W-BOOL-OR-UNSUPPORTED",
                    "OR branches are currently not rendered",
                    Map.of("node", node)));
            return;
        }
        if (node instanceof Not) {
            warnings.add(Issue.warn("W-BOOL-NOT-UNSUPPORTED",
                    "NOT expressions are not rendered",
                    Map.of("node", node)));
            return;
        }
        if (node instanceof Const constant) {
            if (constant == Const.FALSE) {
                warnings.add(Issue.warn("W-CONST-FALSE", "Expression is unsatisfiable", Map.of()));
            }
            return;
        }
        if (node instanceof Atom atom) {
            renderAtom(atom, snapshot, fragments, params, warnings, hits);
        }
    }

    private void renderAtom(Atom atom,
                             ProvenanceSnapshot snapshot,
                             List<String> fragments,
                             Map<String, String> params,
                             List<Issue> warnings,
                             List<RenderTrace.Hit> hits) {
        AtomContext ctx = AtomContext.create(atom);

        ProvenanceSnapshot.RenderRule queryRule = selectRule(snapshot, atom, ProvenanceSnapshot.EmitType.QUERY, false, ctx.matchTypeCode(), ctx.valueType());
        if (queryRule != null && queryRule.template() != null) {
            String fragment = buildQuery(queryRule, ctx);
            if (!fragment.isBlank()) {
                fragments.add(fragment);
                if (hits != null) {
                    hits.add(new RenderTrace.Hit(atom.fieldKey(), atom.operator().name(), queryRule.priority(), ruleId(queryRule)));
                }
            }
        } else {
            warnings.add(Issue.warn("W-RENDER-RULE-MISSING",
                    "No query render rule found",
                    Map.of("fieldKey", atom.fieldKey(), "operator", atom.operator().name())));
        }

        ProvenanceSnapshot.RenderRule paramRule = selectRule(snapshot, atom, ProvenanceSnapshot.EmitType.PARAMS, false, ctx.matchTypeCode(), ctx.valueType());
        if (paramRule != null && !paramRule.params().isEmpty()) {
            applyParams(paramRule, ctx, snapshot, params, warnings, hits);
        }
    }

    private String buildQuery(ProvenanceSnapshot.RenderRule rule, AtomContext ctx) {
        if (ctx.atom.operator() == Atom.Operator.IN) {
            List<String> renderedItems = ctx.rawItems.stream()
                    .map(value -> applyTemplate(Optional.ofNullable(rule.itemTemplate()).orElse("{{v}}"), ctx.placeholders(value)))
                    .collect(Collectors.toList());
            String joined = String.join(Optional.ofNullable(rule.joiner()).orElse(" OR "), renderedItems);
            if (rule.wrapGroup()) {
                joined = "(" + joined + ")";
            }
            return applyTemplate(rule.template(), ctx.basePlaceholders().with("{{items}}", joined));
        }
        return applyTemplate(rule.template(), ctx.basePlaceholders());
    }

    private void applyParams(ProvenanceSnapshot.RenderRule rule,
                              AtomContext ctx,
                              ProvenanceSnapshot snapshot,
                              Map<String, String> params,
                              List<Issue> warnings,
                              List<RenderTrace.Hit> hits) {
        for (Map.Entry<String, String> entry : rule.params().entrySet()) {
            String stdKey = entry.getKey();
            String template = entry.getValue();
            ProvenanceSnapshot.ApiParameter mapping = snapshot.apiParameterMap().get(stdKey);
            if (mapping == null) {
                warnings.add(Issue.warn("W-PARAM-MAP-MISSING",
                        "Standard key lacks provider parameter mapping",
                        Map.of("stdKey", stdKey)));
                continue;
            }
            String value = applyTemplate(template, ctx.basePlaceholders());
            params.put(mapping.providerParamName(), value);
            if (hits != null) {
                hits.add(new RenderTrace.Hit(ctx.atom.fieldKey(), ctx.atom.operator().name(), rule.priority(), ruleId(rule) + "#param:" + stdKey));
            }
        }
    }

    private ProvenanceSnapshot.RenderRule selectRule(ProvenanceSnapshot snapshot,
                                                     Atom atom,
                                                     ProvenanceSnapshot.EmitType emit,
                                                     boolean negated,
                                                     String matchType,
                                                     ProvenanceSnapshot.ValueType valueType) {
        return snapshot.renderRules().stream()
                .filter(rule -> rule.emitType() == emit)
                .filter(rule -> rule.operator() == atom.operator())
                .filter(rule -> Objects.equals(rule.fieldKey(), atom.fieldKey()))
                .filter(rule -> matchesNegation(rule, negated))
                .filter(rule -> matchesMatchType(rule, matchType))
                .filter(rule -> matchesValueType(rule, valueType))
                .max(Comparator.comparingInt(ProvenanceSnapshot.RenderRule::priority))
                .orElse(null);
    }

    private boolean matchesNegation(ProvenanceSnapshot.RenderRule rule, boolean negated) {
        return switch (rule.negation()) {
            case ANY -> true;
            case TRUE -> negated;
            case FALSE -> !negated;
        };
    }

    private boolean matchesMatchType(ProvenanceSnapshot.RenderRule rule, String matchType) {
        if (rule.matchTypeCode() == null || rule.matchTypeCode().isBlank()) {
            return true;
        }
        if (matchType == null) {
            return "ANY".equalsIgnoreCase(rule.matchTypeCode());
        }
        return rule.matchTypeCode().equalsIgnoreCase(matchType);
    }

    private boolean matchesValueType(ProvenanceSnapshot.RenderRule rule, ProvenanceSnapshot.ValueType type) {
        if (rule.valueType() == null || rule.valueType() == ProvenanceSnapshot.ValueType.ANY) {
            return true;
        }
        return rule.valueType() == type;
    }

    private String applyTemplate(String template, PlaceholderMap placeholders) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entries()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String ruleId(ProvenanceSnapshot.RenderRule rule) {
        return rule.fieldKey() + "|" + rule.operator().name() + "|" + rule.emitType();
    }

    private record PlaceholderMap(Map<String, String> delegate) {
        PlaceholderMap {
            delegate = Map.copyOf(delegate);
        }

        PlaceholderMap with(String key, String value) {
            Map<String, String> copy = new LinkedHashMap<>(delegate);
            copy.put(key, value);
            return new PlaceholderMap(copy);
        }

        Set<Map.Entry<String, String>> entries() {
            return delegate.entrySet();
        }

        @SuppressWarnings("unused")
        String get(String key) {
            return delegate.get(key);
        }
    }

    private static class AtomContext {
        private final Atom atom;
        private final PlaceholderMap base;
        private final List<String> rawItems;

        private AtomContext(Atom atom, PlaceholderMap base, List<String> rawItems) {
            this.atom = atom;
            this.base = base;
            this.rawItems = rawItems;
        }

        static AtomContext create(Atom atom) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("{{field}}", atom.fieldKey());
            switch (atom.operator()) {
                case TERM -> populateTerm(placeholders, (Atom.TermValue) atom.value());
                case IN -> {
                    Atom.InValues v = (Atom.InValues) atom.value();
                    List<String> raw = List.copyOf(v.values());
                    List<String> quoted = raw.stream().map(DefaultExprRenderer::quote).toList();
                    placeholders.put("{{items}}", String.join(",", quoted));
                    placeholders.put("{{joined}}", String.join(",", raw));
                    return new AtomContext(atom, new PlaceholderMap(placeholders), raw);
                }
                case RANGE -> populateRange(placeholders, (Atom.RangeValue) atom.value());
                case EXISTS -> {
                    Atom.ExistsFlag flag = (Atom.ExistsFlag) atom.value();
                    placeholders.put("{{exists}}", Boolean.toString(flag.shouldExist()));
                }
                case TOKEN -> {
                    Atom.TokenValue token = (Atom.TokenValue) atom.value();
                    placeholders.put("{{type}}", defaultString(token.tokenType()));
                    placeholders.put("{{value}}", defaultString(token.tokenValue()));
                    placeholders.put("{{token}}", defaultString(token.tokenType()));
                    placeholders.put("{{quoted}}", quote(defaultString(token.tokenValue())));
                }
            }
            return new AtomContext(atom, new PlaceholderMap(placeholders), List.of());
        }

        private static void populateTerm(Map<String, String> placeholders, Atom.TermValue value) {
            String text = defaultString(value.text());
            placeholders.put("{{v}}", text);
            placeholders.put("{{value}}", text);
            placeholders.put("{{quoted}}", quote(text));
            placeholders.put("{{match}}", value.match().name());
            placeholders.put("{{case}}", value.caseSensitivity().name());
        }

        private static void populateRange(Map<String, String> placeholders, Atom.RangeValue value) {
            if (value instanceof Atom.DateRange dr) {
                placeholders.put("{{from}}", formatDate(dr.from()));
                placeholders.put("{{to}}", formatDate(dr.to()));
            } else if (value instanceof Atom.DateTimeRange dtr) {
                placeholders.put("{{from}}", formatInstant(dtr.from()));
                placeholders.put("{{to}}", formatInstant(dtr.to()));
            } else if (value instanceof Atom.NumberRange nr) {
                placeholders.put("{{from}}", formatNumber(nr.from()));
                placeholders.put("{{to}}", formatNumber(nr.to()));
            }
        }

        PlaceholderMap basePlaceholders() {
            return base;
        }

        PlaceholderMap placeholders(String overrideValue) {
            return base.with("{{v}}", overrideValue)
                    .with("{{value}}", overrideValue)
                    .with("{{quoted}}", quote(overrideValue));
        }

        String matchTypeCode() {
            if (atom.operator() == Atom.Operator.TERM) {
                Atom.TermValue value = (Atom.TermValue) atom.value();
                return value.match() == null ? null : value.match().name();
            }
            return null;
        }

        ProvenanceSnapshot.ValueType valueType() {
            return switch (atom.operator()) {
                case RANGE -> {
                    Atom.RangeValue value = (Atom.RangeValue) atom.value();
                    if (value instanceof Atom.DateRange) {
                        yield ProvenanceSnapshot.ValueType.DATE;
                    }
                    if (value instanceof Atom.DateTimeRange) {
                        yield ProvenanceSnapshot.ValueType.DATETIME;
                    }
                    if (value instanceof Atom.NumberRange) {
                        yield ProvenanceSnapshot.ValueType.NUMBER;
                    }
                    yield ProvenanceSnapshot.ValueType.ANY;
                }
                default -> ProvenanceSnapshot.ValueType.STRING;
            };
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String quote(String value) {
        String esc = value.replace("\"", "\\\"");
        return "\"" + esc + "\"";
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String formatNumber(BigDecimal number) {
        return number == null ? "" : number.stripTrailingZeros().toPlainString();
    }
}
