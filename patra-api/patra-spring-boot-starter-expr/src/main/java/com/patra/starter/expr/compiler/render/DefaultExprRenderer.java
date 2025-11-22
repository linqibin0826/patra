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

/// {@link ExprRenderer} 的默认实现，支持完整的 OR/NOT 操作、函数代码执行和仅 std_key 发出。
/// 
/// 主要特性：
/// 
/// - 带有适当括号的 OR/NOT 布尔运算符
///   - PARAMS 规则的函数代码执行
///   - 仅发出 std_keys（不发出提供者参数名称）
///   - SINGLE/MULTI std_key 合并策略
///   - 全面的日志和警告
/// 
/// 参见：docs/expr/02-architecture.md §2.7、docs/expr/03-compiler-bridge-internals.md §3.2.1
/// 
/// @since 1.0.0
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
        "已渲染表达式：queryFragments={}, stdKeyCount={}, warningCount={}",
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
      log.debug("已渲染 OR 表达式，有 {} 个子节点，已包装={}", orFragments.size(), context != RenderContext.OR);
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
      log.debug("已渲染 NOT 表达式，有 {} 个片段", notFragments.size());
    }
  }

  private String buildNotCompositeFragment(List<String> notFragments, Expr child) {
    String inner =
        notFragments.size() == 1 ? notFragments.getFirst() : String.join(" AND ", notFragments);
    String trimmed = inner.trim();

    if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
      trimmed = "(" + trimmed + ")";
    }

    log.debug("已构建 NOT 复合片段：childType={}, result=NOT{}", child.getClass().getSimpleName(), trimmed);

    return "NOT" + trimmed;
  }

  private void renderConstNode(Const constant, List<Issue> warnings) {
    if (constant == Const.FALSE) {
      warnings.add(Issue.warn("W-CONST-FALSE", "表达式不可满足", Map.of()));
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

    // 为两种发出类型预选规则
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

    // 如果可用，渲染 QUERY 片段
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
            "已渲染 QUERY：fieldKey={}, operator={}, negated={}, priority={}",
            atom.fieldKey(),
            atom.operator().name(),
            negated,
            queryRule.priority());
      }
    } else {
      metrics.renderRuleMiss(labels.provenance(), labels.endpoint());
      // 延迟 W-RENDER-RULE-MISSING 发出，直到我们知道 PARAMS 也缺失
    }

    // 渲染 PARAMS std_keys（如果可用）
    if (paramRule != null && !paramRule.params().isEmpty()) {
      metrics.renderRuleHit(labels.provenance(), labels.endpoint());
      applyParams(paramRule, ctx, snapshot, stdKeys, warnings, hits, atom);
    } else if (paramRule == null) {
      metrics.renderRuleMiss(labels.provenance(), labels.endpoint());
    }

    // 如果 QUERY 和 PARAMS 规则都不匹配，则发出一次警告
    if (queryRule == null && paramRule == null) {
      warnings.add(
          Issue.warn(
              "W-RENDER-RULE-MISSING",
              "未找到渲染规则",
              Map.of(
                  "fieldKey",
                  atom.fieldKey(),
                  "operator",
                  atom.operator().name(),
                  "negated",
                  negated)));
      log.warn(
          "缺失渲染规则：fieldKey={}, operator={}, negated={}",
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

    // 执行函数代码（如果存在）
    Map<String, String> placeholders = new LinkedHashMap<>(ctx.basePlaceholders().delegate);
    if (rule.functionCode() != null && !rule.functionCode().isBlank()) {
      if (functionRegistry != null) {
        Optional<RenderFunction> functionOpt = functionRegistry.find(rule.functionCode());
        if (functionOpt.isPresent()) {
          RenderFunction function = functionOpt.get();
          String result = function.apply(placeholders, snapshot);
          // 函数可能修改占位符或返回派生值
          // 如果函数返回非空值，则使用标准占位符名称存储结果
          if (result != null && !result.isBlank()) {
            placeholders.put("{{" + rule.functionCode().toLowerCase() + "}}", result);
            log.debug("已执行 fn_code={}，result={}", rule.functionCode(), result);
          }
        } else {
          warnings.add(
              Issue.warn(
                  "W-FN-OR-TRANSFORM-NOTFOUND", "未找到函数", Map.of("fnCode", rule.functionCode())));
          log.warn("函数未找到：fnCode={}", rule.functionCode());
        }
      } else {
        log.warn("FunctionRegistry 不可用，无法执行 fnCode={}", rule.functionCode());
      }
    }

    PlaceholderMap enrichedPlaceholders = new PlaceholderMap(placeholders);

    // 发出 std_keys（不发出提供者参数名称）
    for (Map.Entry<String, String> entry : rule.params().entrySet()) {
      String stdKey = entry.getKey();
      String template = entry.getValue();
      String value = applyTemplate(template, enrichedPlaceholders);

      // 直接发出 std_key（此处无提供者命名！）
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
          "已发出 std_key: key={}, valueLength={}, priority={}",
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
    // 通用谓词，除了匹配类型，我们在两遍中处理以优先精确匹配
    Stream<ProvenanceSnapshot.RenderRule> base =
        snapshot.renderRules().stream()
            .filter(rule -> rule.emitType() == emit)
            .filter(rule -> Objects.equals(rule.fieldKey(), atom.fieldKey()))
            .filter(rule -> rule.operator() == atom.operator())
            .filter(rule -> matchesNegation(rule, negated))
            .filter(rule -> matchesValueType(rule, valueType));

    Comparator<ProvenanceSnapshot.RenderRule> byPriority =
        Comparator.comparingInt(ProvenanceSnapshot.RenderRule::priority);

    // 第一遍：精确匹配类型（如果提供），或当 matchType 为空时使用 ANY
    Optional<ProvenanceSnapshot.RenderRule> exact =
        base.filter(
                rule -> {
                  if (rule.matchTypeCode() == null || rule.matchTypeCode().isBlank()) return true;
                  if (matchType == null) return "ANY".equalsIgnoreCase(rule.matchTypeCode());
                  return rule.matchTypeCode().equalsIgnoreCase(matchType);
                })
            .max(byPriority);
    if (exact.isPresent()) return exact.get();

    // 第二遍：文本 ANY 的回退 -> 如果可用，使用 PHRASE 规则
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

    // 未找到合适的规则
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
    // 如果规则不约束匹配类型，则是匹配的
    if (rule.matchTypeCode() == null || rule.matchTypeCode().isBlank()) {
      return true;
    }
    // 如果表达式省略了匹配类型，则将其视为 ANY 并允许声明 ANY 的规则
    if (matchType == null) {
      return "ANY".equalsIgnoreCase(rule.matchTypeCode());
    }
    // 精确匹配优先
    if (rule.matchTypeCode().equalsIgnoreCase(matchType)) {
      return true;
    }
    // 回退：当输入是 ANY 时，允许 PHRASE 规则作为安全默认值（带引号的文本）
    // 这符合黄金期望，其中只存在 PHRASE 规则
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

  /// 用于跟踪表达式嵌套以确定括号要求的渲染上下文。
  private enum RenderContext {
    AND,
    OR,
    NOT
  }

  /// 用于 std_key 发出并具有 SINGLE/MULTI 合并策略的累加器。
/// 
/// SINGLE: 根据 (priority DESC, fieldKey ASC, opCode ASC, ruleId ASC) 确定性的最后写入获胜 MULTI:
/// 使用内部分隔符累积所有值
/// 
/// 参见: docs/expr/03-compiler-bridge-internals.md §3.8
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
          log.debug("MULTI std_key 累积: stdKey={}, valueCount={}", stdKey, values.size());
          return;
        }

        if (winner == null || shouldReplace(candidate, winner)) {
          if (winner != null) {
            log.debug(
                "SINGLE std_key 冲突: stdKey={}, 已替换 (new priority={} > old priority={})",
                stdKey,
                candidate.priority(),
                winner.priority());
          }
          winner = candidate;
        } else {
          log.debug("SINGLE std_key 冲突: stdKey={}, 保留现有 (priority={})", stdKey, winner.priority());
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
