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

/// {@link CapabilityChecker} 的默认实现，遍历表达式树并根据 Provenance 快照中的能力定义验证每个原子条件。
///
/// 核心验证逻辑：
///
/// - 字段是否在 Provenance 中注册
///   - 操作符是否被字段支持
///   - 否定是否被允许（整体和特定操作符）
///   - 操作符特定的值约束（长度、模式、范围边界等）
///
/// 严格模式影响：
///
/// - `strictMode=false`: 仅检查结构性约束
///   - `strictMode=true`: 额外检查语义约束（如 TOKEN 空白值）
///
/// @since 0.1.0
public class DefaultCapabilityChecker implements CapabilityChecker {

  /// {@inheritDoc}
  @Override
  public List<Issue> check(Expr expression, ProvenanceSnapshot snapshot, boolean strictMode) {
    Objects.requireNonNull(expression, "expression");
    Objects.requireNonNull(snapshot, "snapshot");
    List<Issue> issues = new ArrayList<>();
    visit(expression, false, snapshot, strictMode, issues);
    return issues;
  }

  /// 递归访问表达式树节点。
  ///
  /// @param node 当前节点
  /// @param underNot 当前节点是否在 NOT 操作符下
  /// @param snapshot Provenance 快照
  /// @param strictMode 是否启用严格模式
  /// @param out 收集的问题列表
  private void visit(
      Expr node,
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
      // 翻转 underNot 标志并继续遍历
      visit(notExpr.child(), !underNot, snapshot, strictMode, out);
      return;
    }
    if (node instanceof Const) {
      // 常量节点无需验证
      return;
    }
    if (node instanceof Atom atom) {
      validateAtom(atom, underNot, snapshot, strictMode, out);
    }
  }

  /// 验证原子条件的完整性和可行性。
  ///
  /// @param atom 原子条件
  /// @param underNot 是否在 NOT 操作符下
  /// @param snapshot Provenance 快照
  /// @param strictMode 是否启用严格模式
  /// @param out 收集的问题列表
  private void validateAtom(
      Atom atom,
      boolean underNot,
      ProvenanceSnapshot snapshot,
      boolean strictMode,
      List<Issue> out) {
    String fieldKey = atom.fieldKey();
    ProvenanceSnapshot.FieldDefinition definition = snapshot.fieldDictionary().get(fieldKey);
    if (definition == null) {
      out.add(
          Issue.error("E-FIELD-NOT-FOUND", "字段未在 Provenance 中注册", Map.of("fieldKey", fieldKey)));
      return;
    }

    ProvenanceSnapshot.Capability capability = snapshot.capabilityMatrix().get(fieldKey);
    if (capability == null) {
      out.add(Issue.error("E-CAPABILITY-MISSING", "字段缺少能力定义", Map.of("fieldKey", fieldKey)));
      return;
    }

    String op = atom.operator().name();
    if (!capability.ops().contains(op)) {
      out.add(
          Issue.error(
              "E-OP-NOT-ALLOWED", "字段不支持该操作符", Map.of("fieldKey", fieldKey, "operator", op)));
      return;
    }

    // 检查否定支持
    if (underNot) {
      if (!capability.supportsNot()) {
        out.add(Issue.error("E-NOT-UNSUPPORTED", "该字段不支持否定", Map.of("fieldKey", fieldKey)));
      } else if (!capability.negatableOps().isEmpty() && !capability.negatableOps().contains(op)) {
        out.add(
            Issue.error(
                "E-NOT-OP-UNSUPPORTED", "该操作符不支持否定", Map.of("fieldKey", fieldKey, "operator", op)));
      }
    }

    // 根据操作符类型进行特定验证
    switch (atom.operator()) {
      case TERM -> validateTerm(atom, capability, out);
      case IN -> validateIn(atom, capability, out);
      case RANGE -> validateRange(atom, capability, out);
      case EXISTS -> validateExists(capability, fieldKey, out);
      case TOKEN -> validateToken(atom, capability, strictMode, out);
    }
  }

  /// 验证 TERM 操作符的值约束。
  ///
  /// 检查项：
  ///
  /// - 空白值限制
  ///   - 最小/最大长度
  ///   - 正则表达式模式
  ///   - 大小写敏感性
  ///   - 匹配策略支持（PHRASE、WILDCARD 等）
  ///
  private void validateTerm(Atom atom, ProvenanceSnapshot.Capability capability, List<Issue> out) {
    Atom.TermValue value = (Atom.TermValue) atom.value();
    String text = value.text();
    if ((text == null || text.isBlank()) && !capability.termAllowBlank()) {
      out.add(Issue.error("E-TERM-BLANK", "TERM 不允许空白值", Map.of("fieldKey", atom.fieldKey())));
      return;
    }

    if (text != null) {
      int length = text.length();
      if (capability.termMinLength() > 0 && length < capability.termMinLength()) {
        out.add(
            Issue.error(
                "E-TERM-LEN-MIN",
                "TERM 长度小于最小值",
                Map.of(
                    "fieldKey",
                    atom.fieldKey(),
                    "length",
                    length,
                    "min",
                    capability.termMinLength())));
      }
      if (capability.termMaxLength() > 0 && length > capability.termMaxLength()) {
        out.add(
            Issue.error(
                "E-TERM-LEN-MAX",
                "TERM 长度超过最大值",
                Map.of(
                    "fieldKey",
                    atom.fieldKey(),
                    "length",
                    length,
                    "max",
                    capability.termMaxLength())));
      }
      if (capability.termPattern() != null && !capability.termPattern().isBlank()) {
        if (!text.matches(capability.termPattern())) {
          out.add(
              Issue.error(
                  "E-TERM-PATTERN",
                  "TERM 不符合模式约束",
                  Map.of("fieldKey", atom.fieldKey(), "pattern", capability.termPattern())));
        }
      }
    }

    CaseSensitivity cs = value.caseSensitivity();
    if (cs.isSensitive() && !capability.termCaseSensitiveAllowed()) {
      out.add(
          Issue.error(
              "E-TERM-CASE-SENSITIVE", "不支持大小写敏感的 TERM", Map.of("fieldKey", atom.fieldKey())));
    }

    Set<String> matches = capability.termMatches();
    if (matches != null && !matches.isEmpty()) {
      String match = value.match().name().toUpperCase(Locale.ROOT);
      if (!matches.contains(match)) {
        out.add(
            Issue.error(
                "E-TERM-MATCH-UNSUPPORTED",
                "不支持该匹配策略",
                Map.of("fieldKey", atom.fieldKey(), "match", match)));
      }
    }
  }

  /// 验证 IN 操作符的值约束。
  ///
  /// @param atom 原子条件
  /// @param capability 能力定义
  /// @param out 收集的问题列表
  private void validateIn(Atom atom, ProvenanceSnapshot.Capability capability, List<Issue> out) {
    Atom.InValues values = (Atom.InValues) atom.value();
    if (values.values().isEmpty()) {
      out.add(Issue.error("E-IN-EMPTY", "IN 至少需要一个值", Map.of("fieldKey", atom.fieldKey())));
      return;
    }
    if (capability.inMaxSize() > 0 && values.values().size() > capability.inMaxSize()) {
      out.add(
          Issue.error(
              "E-IN-SIZE",
              "IN 值数量超过最大限制",
              Map.of(
                  "fieldKey",
                  atom.fieldKey(),
                  "size",
                  values.values().size(),
                  "max",
                  capability.inMaxSize())));
    }
    if (values.caseSensitivity().isSensitive() && !capability.inCaseSensitiveAllowed()) {
      out.add(
          Issue.error("E-IN-CASE-SENSITIVE", "不支持大小写敏感的 IN", Map.of("fieldKey", atom.fieldKey())));
    }
  }

  /// 验证 RANGE 操作符的类型和边界约束。
  ///
  /// @param atom 原子条件
  /// @param capability 能力定义
  /// @param out 收集的问题列表
  private void validateRange(Atom atom, ProvenanceSnapshot.Capability capability, List<Issue> out) {
    ProvenanceSnapshot.RangeKind kind = capability.rangeKind();
    Atom.RangeValue value = (Atom.RangeValue) atom.value();
    switch (value) {
      case Atom.DateRange dr -> {
        if (kind != ProvenanceSnapshot.RangeKind.DATE) {
          complainRangeKind(atom, kind, "DATE", out);
        }
        validateDateBounds(
            atom.fieldKey(), dr.from(), dr.to(), capability.dateMin(), capability.dateMax(), out);
      }
      case Atom.DateTimeRange dtr -> {
        if (kind != ProvenanceSnapshot.RangeKind.DATETIME) {
          complainRangeKind(atom, kind, "DATETIME", out);
        }
        if (dtr.from() == null && dtr.to() == null) {
          out.add(
              Issue.error("E-RANGE-OPEN", "日期时间范围必须至少指定一个边界", Map.of("fieldKey", atom.fieldKey())));
        }
      }
      case Atom.NumberRange nr -> {
        if (kind != ProvenanceSnapshot.RangeKind.NUMBER) {
          complainRangeKind(atom, kind, "NUMBER", out);
        }
        if (nr.from() == null && nr.to() == null) {
          out.add(
              Issue.error("E-RANGE-OPEN", "数值范围必须至少指定一个边界", Map.of("fieldKey", atom.fieldKey())));
        }
      }
    }
  }

  /// 验证日期范围的边界是否在能力定义的最小/最大值内。
  ///
  /// @param fieldKey 字段键
  /// @param from 开始日期
  /// @param to 结束日期
  /// @param min 能力定义的最小日期
  /// @param max 能力定义的最大日期
  /// @param out 收集的问题列表
  private void validateDateBounds(
      String fieldKey,
      LocalDate from,
      LocalDate to,
      LocalDate min,
      LocalDate max,
      List<Issue> out) {
    if (from == null && to == null) {
      out.add(Issue.error("E-RANGE-OPEN", "日期范围必须至少指定一个边界", Map.of("fieldKey", fieldKey)));
      return;
    }
    if (from != null && min != null && from.isBefore(min)) {
      out.add(
          Issue.error(
              "E-DATE-MIN", "日期下界早于能力最小值", Map.of("fieldKey", fieldKey, "from", from, "min", min)));
    }
    if (to != null && max != null && to.isAfter(max)) {
      out.add(
          Issue.error(
              "E-DATE-MAX", "日期上界晚于能力最大值", Map.of("fieldKey", fieldKey, "to", to, "max", max)));
    }
  }

  /// 报告范围类型不匹配错误。
  ///
  /// @param atom 原子条件
  /// @param actual 实际的范围类型
  /// @param expected 期望的范围类型
  /// @param out 收集的问题列表
  private void complainRangeKind(
      Atom atom, ProvenanceSnapshot.RangeKind actual, String expected, List<Issue> out) {
    out.add(
        Issue.error(
            "E-RANGE-KIND",
            "范围类型不匹配",
            Map.of("fieldKey", atom.fieldKey(), "expected", expected, "actual", actual)));
  }

  /// 验证 EXISTS 操作符是否被支持。
  ///
  /// @param capability 能力定义
  /// @param fieldKey 字段键
  /// @param out 收集的问题列表
  private void validateExists(
      ProvenanceSnapshot.Capability capability, String fieldKey, List<Issue> out) {
    if (!capability.existsSupported()) {
      out.add(Issue.error("E-EXISTS-UNSUPPORTED", "不支持 EXISTS 操作符", Map.of("fieldKey", fieldKey)));
    }
  }

  /// 验证 TOKEN 操作符的值约束。
  ///
  /// @param atom 原子条件
  /// @param capability 能力定义
  /// @param strictMode 是否启用严格模式
  /// @param out 收集的问题列表
  private void validateToken(
      Atom atom, ProvenanceSnapshot.Capability capability, boolean strictMode, List<Issue> out) {
    Atom.TokenValue token = (Atom.TokenValue) atom.value();
    if (token.tokenValue() == null || token.tokenValue().isBlank()) {
      if (strictMode) {
        out.add(
            Issue.error("E-TOKEN-BLANK", "严格模式下 Token 值不能为空", Map.of("fieldKey", atom.fieldKey())));
      }
    }

    Set<String> kinds = capability.tokenKinds();
    if (kinds != null && !kinds.isEmpty()) {
      String type = token.tokenType() == null ? "" : token.tokenType().toLowerCase(Locale.ROOT);
      if (!kinds.contains(type)) {
        out.add(
            Issue.error(
                "E-TOKEN-KIND",
                "不支持该 Token 类型",
                Map.of("fieldKey", atom.fieldKey(), "tokenType", token.tokenType())));
      }
    }

    if (capability.tokenValuePattern() != null && !capability.tokenValuePattern().isBlank()) {
      String value = token.tokenValue();
      if (value != null && !value.matches(capability.tokenValuePattern())) {
        out.add(
            Issue.error(
                "E-TOKEN-PATTERN",
                "Token 值不符合模式约束",
                Map.of("fieldKey", atom.fieldKey(), "pattern", capability.tokenValuePattern())));
      }
    }
  }
}
