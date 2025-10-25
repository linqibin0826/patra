package com.patra.starter.expr.compiler.render;

import com.patra.expr.And;
import com.patra.expr.Atom;
import com.patra.expr.Const;
import com.patra.expr.Expr;
import com.patra.expr.Not;
import com.patra.expr.Or;
import com.patra.starter.expr.compiler.function.FunctionRegistry;
import com.patra.starter.expr.compiler.function.RenderFunction;
import com.patra.starter.expr.compiler.metrics.ExprMetrics;
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
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ExprRenderer} with full OR/NOT support, fn_code execution, and
 * std_key-only emission.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>OR/NOT boolean operators with proper parentheses
 *   <li>fn_code execution for PARAMS rules
 *   <li>Emits std_keys only (no provider parameter naming)
 *   <li>SINGLE/MULTI std_key merge policies
 *   <li>Comprehensive logging and warnings
 * </ul>
 *
 * <p>See: docs/expr/02-architecture.md §2.7, docs/expr/03-compiler-bridge-internals.md §3.2.1
 *
 * @since 1.0.0
 */
public class DefaultExprRenderer implements ExprRenderer {

  private static final Logger log = LoggerFactory.getLogger(DefaultExprRenderer.class);
  private static final String MULTI_DELIMITER = "||";

  private final FunctionRegistry functionRegistry;
  private final ExprMetrics metrics;

  public DefaultExprRenderer() {
    this(null, ExprMetrics.noop());
  }

  public DefaultExprRenderer(FunctionRegistry functionRegistry) {
    this(functionRegistry, ExprMetrics.noop());
  }

  public DefaultExprRenderer(FunctionRegistry functionRegistry, ExprMetrics metrics) {
    this.functionRegistry = functionRegistry;
    this.metrics = metrics == null ? ExprMetrics.noop() : metrics;
  }

  @Override
  public RenderOutcome render(Expr expression, ProvenanceSnapshot snapshot, boolean traceEnabled) {
    Objects.requireNonNull(expression, "expression");
    Objects.requireNonNull(snapshot, "snapshot");

    List<String> queryFragments = new ArrayList<>();
    StdKeyAccumulator stdKeyAccumulator = new StdKeyAccumulator();
    List<Issue> warnings = new ArrayList<>();
    List<RenderTrace.Hit> hits = traceEnabled ? new ArrayList<>() : null;

    RenderLabels labels = RenderLabels.from(snapshot);

    renderNode(
        expression,
        snapshot,
        labels,
        RenderContext.AND,
        false,
        queryFragments,
        stdKeyAccumulator,
        warnings,
        hits);

    String query = String.join(" AND ", queryFragments);
    Map<String, String> stdKeyParams = stdKeyAccumulator.toMap();

    log.debug(
        "Rendered expression: queryFragments={}, stdKeyCount={}, warningCount={}",
        queryFragments.size(),
        stdKeyParams.size(),
        warnings.size());

    RenderTrace trace = traceEnabled ? new RenderTrace(hits) : null;
    return new RenderOutcome(query, stdKeyParams, warnings, trace);
  }

  private void renderNode(
      Expr node,
      ProvenanceSnapshot snapshot,
      RenderLabels labels,
      RenderContext context,
      boolean negated,
      List<String> fragments,
      StdKeyAccumulator stdKeys,
      List<Issue> warnings,
      List<RenderTrace.Hit> hits) {

    if (node instanceof And andExpr) {
      renderAndNode(
          andExpr, snapshot, labels, context, negated, fragments, stdKeys, warnings, hits);
      return;
    }

    if (node instanceof Or orExpr) {
      renderOrNode(orExpr, snapshot, labels, context, negated, fragments, stdKeys, warnings, hits);
      return;
    }

    if (node instanceof Not notExpr) {
      renderNotNode(
          notExpr, snapshot, labels, context, negated, fragments, stdKeys, warnings, hits);
      return;
    }

    if (node instanceof Const constant) {
      renderConstNode(constant, warnings);
      return;
    }

    if (node instanceof Atom atom) {
      renderAtom(atom, snapshot, labels, negated, fragments, stdKeys, warnings, hits);
    }
  }

  private void renderAndNode(
      And andExpr,
      ProvenanceSnapshot snapshot,
      RenderLabels labels,
      RenderContext context,
      boolean negated,
      List<String> fragments,
      StdKeyAccumulator stdKeys,
      List<Issue> warnings,
      List<RenderTrace.Hit> hits) {
    if (context == RenderContext.AND) {
      andExpr
          .children()
          .forEach(
              child ->
                  renderNode(
                      child,
                      snapshot,
                      labels,
                      RenderContext.AND,
                      negated,
                      fragments,
                      stdKeys,
                      warnings,
                      hits));
    } else {
      List<String> nested = new ArrayList<>();
      andExpr
          .children()
          .forEach(
              child ->
                  renderNode(
                      child,
                      snapshot,
                      labels,
                      RenderContext.AND,
                      negated,
                      nested,
                      stdKeys,
                      warnings,
                      hits));
      if (!nested.isEmpty()) {
        String joined = String.join(" AND ", nested);
        if (context == RenderContext.OR) {
          joined = "(" + joined + ")";
        }
        fragments.add(joined);
      }
    }
  }

  private void renderOrNode(
      Or orExpr,
      ProvenanceSnapshot snapshot,
      RenderLabels labels,
      RenderContext context,
      boolean negated,
      List<String> fragments,
      StdKeyAccumulator stdKeys,
      List<Issue> warnings,
      List<RenderTrace.Hit> hits) {
    List<String> orFragments = new ArrayList<>();
    orExpr
        .children()
        .forEach(
            child ->
                renderNode(
                    child,
                    snapshot,
                    labels,
                    RenderContext.OR,
                    negated,
                    orFragments,
                    stdKeys,
                    warnings,
                    hits));

    if (!orFragments.isEmpty()) {
      String joined = String.join(" OR ", orFragments);
      if (context != RenderContext.OR) {
        joined = "(" + joined + ")";
      }
      fragments.add(joined);
      log.debug(
          "Rendered OR expression with {} children, wrapped={}",
          orFragments.size(),
          context != RenderContext.OR);
    }
  }

  private void renderNotNode(
      Not notExpr,
      ProvenanceSnapshot snapshot,
      RenderLabels labels,
      RenderContext context,
      boolean negated,
      List<String> fragments,
      StdKeyAccumulator stdKeys,
      List<Issue> warnings,
      List<RenderTrace.Hit> hits) {
    Expr child = notExpr.child();
    boolean compositeChild = child instanceof And || child instanceof Or;
    List<String> notFragments = new ArrayList<>();

    renderNode(
        child,
        snapshot,
        labels,
        RenderContext.NOT,
        !compositeChild,
        notFragments,
        stdKeys,
        warnings,
        hits);

    if (!notFragments.isEmpty()) {
      if (compositeChild) {
        String fragment = buildNotCompositeFragment(notFragments, child);
        fragments.add(fragment);
      } else {
        fragments.addAll(notFragments);
      }
      log.debug("Rendered NOT expression with {} fragments", notFragments.size());
    }
  }

  private String buildNotCompositeFragment(List<String> notFragments, Expr child) {
    String inner =
        notFragments.size() == 1 ? notFragments.getFirst() : String.join(" AND ", notFragments);
    String trimmed = inner.trim();

    if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
      trimmed = "(" + trimmed + ")";
    }

    log.debug(
        "Built NOT composite fragment: childType={}, result=NOT{}",
        child.getClass().getSimpleName(),
        trimmed);

    return "NOT" + trimmed;
  }

  private void renderConstNode(Const constant, List<Issue> warnings) {
    if (constant == Const.FALSE) {
      warnings.add(Issue.warn("W-CONST-FALSE", "Expression is unsatisfiable", Map.of()));
    }
  }

  private void renderAtom(
      Atom atom,
      ProvenanceSnapshot snapshot,
      RenderLabels labels,
      boolean negated,
      List<String> fragments,
      StdKeyAccumulator stdKeys,
      List<Issue> warnings,
      List<RenderTrace.Hit> hits) {

    AtomContext ctx = AtomContext.create(atom);

    // Pre-select rules for both emit types
    ProvenanceSnapshot.RenderRule queryRule =
        selectRule(
            snapshot,
            atom,
            ProvenanceSnapshot.EmitType.QUERY,
            negated,
            ctx.matchTypeCode(),
            ctx.valueType());

    ProvenanceSnapshot.RenderRule paramRule =
        selectRule(
            snapshot,
            atom,
            ProvenanceSnapshot.EmitType.PARAMS,
            negated,
            ctx.matchTypeCode(),
            ctx.valueType());

    // Render QUERY fragment if available
    if (queryRule != null && queryRule.template() != null) {
      metrics.renderRuleHit(labels.provenance(), labels.endpoint());
      String fragment = buildQuery(queryRule, ctx);
      if (!fragment.isBlank()) {
        fragments.add(fragment);
        if (hits != null) {
          hits.add(
              new RenderTrace.Hit(
                  atom.fieldKey(),
                  atom.operator().name(),
                  queryRule.priority(),
                  ruleId(queryRule)));
        }
        log.debug(
            "Rendered QUERY: fieldKey={}, operator={}, negated={}, priority={}",
            atom.fieldKey(),
            atom.operator().name(),
            negated,
            queryRule.priority());
      }
    } else {
      metrics.renderRuleMiss(labels.provenance(), labels.endpoint());
      // Defer W-RENDER-RULE-MISSING emission until we know PARAMS is also missing
    }

    // Render PARAMS std_keys if available
    if (paramRule != null && !paramRule.params().isEmpty()) {
      metrics.renderRuleHit(labels.provenance(), labels.endpoint());
      applyParams(paramRule, ctx, snapshot, stdKeys, warnings, hits, atom);
    } else if (paramRule == null) {
      metrics.renderRuleMiss(labels.provenance(), labels.endpoint());
    }

    // If neither QUERY nor PARAMS rule matched, emit warning once
    if (queryRule == null && paramRule == null) {
      warnings.add(
          Issue.warn(
              "W-RENDER-RULE-MISSING",
              "No render rule found",
              Map.of(
                  "fieldKey",
                  atom.fieldKey(),
                  "operator",
                  atom.operator().name(),
                  "negated",
                  negated)));
      log.warn(
          "Missing render rule: fieldKey={}, operator={}, negated={}",
          atom.fieldKey(),
          atom.operator().name(),
          negated);
    }
  }

  private String buildQuery(ProvenanceSnapshot.RenderRule rule, AtomContext ctx) {
    if (ctx.atom.operator() == Atom.Operator.IN) {
      List<String> renderedItems =
          ctx.rawItems.stream()
              .map(
                  value ->
                      applyTemplate(
                          Optional.ofNullable(rule.itemTemplate()).orElse("{{v}}"),
                          ctx.placeholders(value)))
              .collect(Collectors.toList());
      String joined = String.join(Optional.ofNullable(rule.joiner()).orElse(" OR "), renderedItems);
      if (rule.wrapGroup()) {
        joined = "(" + joined + ")";
      }
      return applyTemplate(rule.template(), ctx.basePlaceholders().with("{{items}}", joined));
    }
    return applyTemplate(rule.template(), ctx.basePlaceholders());
  }

  private void applyParams(
      ProvenanceSnapshot.RenderRule rule,
      AtomContext ctx,
      ProvenanceSnapshot snapshot,
      StdKeyAccumulator stdKeys,
      List<Issue> warnings,
      List<RenderTrace.Hit> hits,
      Atom atom) {

    // Execute fn_code if present
    Map<String, String> placeholders = new LinkedHashMap<>(ctx.basePlaceholders().delegate);
    if (rule.functionCode() != null && !rule.functionCode().isBlank()) {
      if (functionRegistry != null) {
        Optional<RenderFunction> functionOpt = functionRegistry.find(rule.functionCode());
        if (functionOpt.isPresent()) {
          RenderFunction function = functionOpt.get();
          String result = function.apply(placeholders, snapshot);
          // Function may modify placeholders or return a derived value
          // Store result with a standard placeholder name if function returns non-null
          if (result != null && !result.isBlank()) {
            placeholders.put("{{" + rule.functionCode().toLowerCase() + "}}", result);
            log.debug("Executed fn_code={}, result={}", rule.functionCode(), result);
          }
        } else {
          warnings.add(
              Issue.warn(
                  "W-FN-OR-TRANSFORM-NOTFOUND",
                  "Function not found",
                  Map.of("fnCode", rule.functionCode())));
          log.warn("Function not found: fnCode={}", rule.functionCode());
        }
      } else {
        log.warn("FunctionRegistry not available, cannot execute fnCode={}", rule.functionCode());
      }
    }

    PlaceholderMap enrichedPlaceholders = new PlaceholderMap(placeholders);

    // Emit std_keys (NOT provider parameter names)
    for (Map.Entry<String, String> entry : rule.params().entrySet()) {
      String stdKey = entry.getKey();
      String template = entry.getValue();
      String value = applyTemplate(template, enrichedPlaceholders);

      // Emit std_key directly (no provider naming here!)
      boolean multi = isMulti(snapshot, atom.fieldKey(), stdKey);
      stdKeys.add(
          stdKey,
          value,
          multi,
          rule.priority(),
          atom.fieldKey(),
          atom.operator().name(),
          ruleId(rule));

      if (hits != null) {
        hits.add(
            new RenderTrace.Hit(
                atom.fieldKey(),
                atom.operator().name(),
                rule.priority(),
                ruleId(rule) + "#param:" + stdKey));
      }

      log.debug(
          "Emitted std_key: key={}, valueLength={}, priority={}",
          stdKey,
          value.length(),
          rule.priority());
    }
  }

  private ProvenanceSnapshot.RenderRule selectRule(
      ProvenanceSnapshot snapshot,
      Atom atom,
      ProvenanceSnapshot.EmitType emit,
      boolean negated,
      String matchType,
      ProvenanceSnapshot.ValueType valueType) {
    // Common predicate except match type, which we handle in two passes to prefer exact match
    Stream<ProvenanceSnapshot.RenderRule> base =
        snapshot.renderRules().stream()
            .filter(rule -> rule.emitType() == emit)
            .filter(rule -> Objects.equals(rule.fieldKey(), atom.fieldKey()))
            .filter(rule -> rule.operator() == atom.operator())
            .filter(rule -> matchesNegation(rule, negated))
            .filter(rule -> matchesValueType(rule, valueType));

    Comparator<ProvenanceSnapshot.RenderRule> byPriority =
        Comparator.comparingInt(ProvenanceSnapshot.RenderRule::priority);

    // Pass 1: exact matchType if provided, or ANY when matchType is null
    Optional<ProvenanceSnapshot.RenderRule> exact =
        base.filter(
                rule -> {
                  if (rule.matchTypeCode() == null || rule.matchTypeCode().isBlank()) return true;
                  if (matchType == null) return "ANY".equalsIgnoreCase(rule.matchTypeCode());
                  return rule.matchTypeCode().equalsIgnoreCase(matchType);
                })
            .max(byPriority);
    if (exact.isPresent()) return exact.get();

    // Pass 2: fallback for text ANY -> use PHRASE rule if available
    if (matchType != null && "ANY".equalsIgnoreCase(matchType)) {
      Optional<ProvenanceSnapshot.RenderRule> phrase =
          snapshot.renderRules().stream()
              .filter(rule -> rule.emitType() == emit)
              .filter(rule -> Objects.equals(rule.fieldKey(), atom.fieldKey()))
              .filter(rule -> rule.operator() == atom.operator())
              .filter(rule -> matchesNegation(rule, negated))
              .filter(rule -> matchesValueType(rule, valueType))
              .filter(rule -> "PHRASE".equalsIgnoreCase(rule.matchTypeCode()))
              .max(byPriority);
      if (phrase.isPresent()) return phrase.get();
    }

    // No suitable rule found
    return null;
  }

  private boolean matchesNegation(ProvenanceSnapshot.RenderRule rule, boolean negated) {
    return switch (rule.negation()) {
      case ANY -> true;
      case TRUE -> negated;
      case FALSE -> !negated;
    };
  }

  private boolean matchesMatchType(ProvenanceSnapshot.RenderRule rule, String matchType) {
    // If the rule does not constrain match type, it's a match.
    if (rule.matchTypeCode() == null || rule.matchTypeCode().isBlank()) {
      return true;
    }
    // If the expression omitted match type, treat as ANY and allow rules that declare ANY.
    if (matchType == null) {
      return "ANY".equalsIgnoreCase(rule.matchTypeCode());
    }
    // Exact match first.
    if (rule.matchTypeCode().equalsIgnoreCase(matchType)) {
      return true;
    }
    // Fallback: when input is ANY, allow PHRASE rule as a safe default (quoted text).
    // This aligns with golden expectations where ONLY PHRASE rule exists.
    return "ANY".equalsIgnoreCase(matchType) && "PHRASE".equalsIgnoreCase(rule.matchTypeCode());
  }

  private boolean matchesValueType(
      ProvenanceSnapshot.RenderRule rule, ProvenanceSnapshot.ValueType type) {
    if (rule.valueType() == null || rule.valueType() == ProvenanceSnapshot.ValueType.ANY) {
      return true;
    }
    return rule.valueType() == type;
  }

  private boolean isMulti(
      ProvenanceSnapshot snapshot, String fieldKey, String stdKeyOrFieldFallback) {
    ProvenanceSnapshot.FieldDefinition fieldDefinition = snapshot.fieldDictionary().get(fieldKey);
    if (fieldDefinition != null
        && fieldDefinition.cardinality() == ProvenanceSnapshot.Cardinality.MULTI) {
      return true;
    }
    ProvenanceSnapshot.FieldDefinition stdKeyDefinition =
        snapshot.fieldDictionary().get(stdKeyOrFieldFallback);
    return stdKeyDefinition != null
        && stdKeyDefinition.cardinality() == ProvenanceSnapshot.Cardinality.MULTI;
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

  private record RenderLabels(String provenance, String endpoint) {
    private static RenderLabels from(ProvenanceSnapshot snapshot) {
      String provenance =
          snapshot != null && snapshot.identity() != null
              ? defaultString(snapshot.identity().code())
              : "UNKNOWN";
      String endpoint =
          snapshot != null && snapshot.operation() != null
              ? defaultString(snapshot.operation().code())
              : "UNKNOWN";
      return new RenderLabels(provenance, endpoint);
    }
  }

  /** Render context for tracking expression nesting to determine parentheses requirements. */
  private enum RenderContext {
    AND,
    OR,
    NOT
  }

  /**
   * Accumulator for std_key emissions with SINGLE/MULTI merge policy.
   *
   * <p>SINGLE: deterministic last-write-wins by (priority DESC, fieldKey ASC, opCode ASC, ruleId
   * ASC) MULTI: accumulates all values with internal delimiter
   *
   * <p>See: docs/expr/03-compiler-bridge-internals.md §3.8
   */
  private static class StdKeyAccumulator {
    private final Map<String, StdKeyEntry> entries = new LinkedHashMap<>();

    void add(
        String stdKey,
        String value,
        boolean multi,
        int priority,
        String fieldKey,
        String opCode,
        String ruleId) {
      StdKeyValue candidate = new StdKeyValue(value, priority, fieldKey, opCode, ruleId);
      StdKeyEntry entry = entries.computeIfAbsent(stdKey, StdKeyEntry::new);
      entry.add(candidate, multi);
    }

    Map<String, String> toMap() {
      return entries.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  entry -> entry.getValue().renderedValue(),
                  (a, b) -> a,
                  LinkedHashMap::new));
    }

    private static boolean shouldReplace(StdKeyValue candidate, StdKeyValue current) {
      if (candidate.priority() != current.priority()) {
        return candidate.priority() > current.priority();
      }
      int fieldCmp = candidate.fieldKey().compareTo(current.fieldKey());
      if (fieldCmp != 0) {
        return fieldCmp < 0;
      }
      int opCmp = candidate.opCode().compareTo(current.opCode());
      if (opCmp != 0) {
        return opCmp < 0;
      }
      return candidate.ruleId().compareTo(current.ruleId()) < 0;
    }

    private static int compareForOrdering(StdKeyValue left, StdKeyValue right) {
      if (left.priority() != right.priority()) {
        return Integer.compare(right.priority(), left.priority());
      }
      int fieldCmp = left.fieldKey().compareTo(right.fieldKey());
      if (fieldCmp != 0) {
        return fieldCmp;
      }
      int opCmp = left.opCode().compareTo(right.opCode());
      if (opCmp != 0) {
        return opCmp;
      }
      return left.ruleId().compareTo(right.ruleId());
    }

    private static class StdKeyEntry {
      private final String stdKey;
      private boolean multi;
      private StdKeyValue winner;
      private final List<StdKeyValue> values = new ArrayList<>();

      StdKeyEntry(String stdKey) {
        this.stdKey = stdKey;
      }

      void add(StdKeyValue candidate, boolean candidateMulti) {
        if (candidateMulti || multi) {
          if (!multi) {
            multi = true;
            if (winner != null) {
              values.add(winner);
              winner = null;
            }
          }
          values.add(candidate);
          values.sort(StdKeyAccumulator::compareForOrdering);
          log.debug("MULTI std_key accumulation: stdKey={}, valueCount={}", stdKey, values.size());
          return;
        }

        if (winner == null || shouldReplace(candidate, winner)) {
          if (winner != null) {
            log.debug(
                "SINGLE std_key collision: stdKey={}, replaced (new priority={} > old priority={})",
                stdKey,
                candidate.priority(),
                winner.priority());
          }
          winner = candidate;
        } else {
          log.debug(
              "SINGLE std_key collision: stdKey={}, kept existing (priority={})",
              stdKey,
              winner.priority());
        }
      }

      String renderedValue() {
        if (multi) {
          return values.stream()
              .sorted(StdKeyAccumulator::compareForOrdering)
              .map(StdKeyValue::value)
              .collect(Collectors.joining(MULTI_DELIMITER));
        }
        return winner == null ? "" : winner.value();
      }
    }

    private record StdKeyValue(
        String value, int priority, String fieldKey, String opCode, String ruleId) {}
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
