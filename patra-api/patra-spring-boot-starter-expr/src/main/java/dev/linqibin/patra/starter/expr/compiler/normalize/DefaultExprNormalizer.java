package dev.linqibin.patra.starter.expr.compiler.normalize;

import dev.linqibin.patra.expr.And;
import dev.linqibin.patra.expr.Atom;
import dev.linqibin.patra.expr.Const;
import dev.linqibin.patra.expr.Expr;
import dev.linqibin.patra.expr.Exprs;
import dev.linqibin.patra.expr.Not;
import dev.linqibin.patra.expr.Or;
import dev.linqibin.patra.expr.TextMatch;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// {@link ExprNormalizer} 的默认实现,通过消除冗余、扁平化嵌套和去重来简化表达式。
///
/// 主要规范化策略:
///
/// - **TERM**: 修剪空白字符
///   - **IN**: 去重、移除空白值、单元素转 TERM、空列表转 FALSE
///   - **AND**: 扁平化嵌套 AND、移除 TRUE、发现 FALSE 则短路为 FALSE、去重
///   - **OR**: 扁平化嵌套 OR、移除 FALSE、发现 TRUE 则短路为 TRUE、去重
///   - **NOT**: 双重否定消除 (NOT NOT x → x)、常量翻转 (NOT TRUE → FALSE)
///
/// 规范化保持语义等价性,同时产生更简单、更紧凑的表达式树。
///
/// @author linqibin
/// @since 0.1.0
public class DefaultExprNormalizer implements ExprNormalizer {

  @Override
  public Expr normalize(Expr expression, boolean strictMode) {
    Objects.requireNonNull(expression, "expression");
    return normalizeNode(expression);
  }

  /// 递归规范化表达式节点。
  ///
  /// @param expr 待规范化的表达式节点
  /// @return 规范化后的表达式
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

  /// 规范化原子条件,仅处理 TERM 和 IN 操作符。
  ///
  /// @param atom 原子条件
  /// @return 规范化后的表达式
  private Expr normalizeAtom(Atom atom) {
    return switch (atom.operator()) {
      case TERM -> normalizeTerm(atom);
      case IN -> normalizeIn(atom);
      default -> atom;
    };
  }

  /// 规范化 TERM 操作符:修剪文本空白。
  ///
  /// @param atom TERM 原子条件
  /// @return 规范化后的表达式
  private Expr normalizeTerm(Atom atom) {
    Atom.TermValue term = (Atom.TermValue) atom.value();
    String text = term.text();
    String trimmed = text == null ? null : text.trim();
    if (Objects.equals(text, trimmed)) {
      return atom;
    }
    return Exprs.term(atom.fieldKey(), trimmed, term.match(), term.caseSensitivity());
  }

  /// 规范化 IN 操作符:去重、移除空白值、优化单元素和空列表。
  ///
  /// 转换规则:
  ///
  /// - 空列表 → Const.FALSE
  ///   - 单元素 → TERM (PHRASE 匹配)
  ///   - 多元素 → 去重后的 IN
  ///
  /// @param atom IN 原子条件
  /// @return 规范化后的表达式
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
      // 根据大小写敏感性去重
      String key =
          values.caseSensitivity().isSensitive() ? trimmed : trimmed.toLowerCase(Locale.ROOT);
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

  /// 规范化 AND 表达式:扁平化、短路求值、去重。
  ///
  /// 优化规则:
  ///
  /// - 发现 FALSE → 整个表达式为 FALSE (短路)
  ///   - 移除所有 TRUE 子节点
  ///   - 扁平化嵌套 AND (AND(AND(a,b),c) → AND(a,b,c))
  ///   - 去重相同的子表达式
  ///   - 空列表 → TRUE,单元素 → 该元素本身
  ///
  /// @param andExpr AND 表达式
  /// @return 规范化后的表达式
  private Expr normalizeAnd(And andExpr) {
    List<Expr> normalized = new ArrayList<>();
    boolean hasFalse = false;
    for (Expr child : andExpr.children()) {
      Expr n = normalizeNode(child);
      if (n == Const.FALSE) {
        hasFalse = true;
        break; // 短路:AND 遇到 FALSE 即为 FALSE
      }
      if (n != Const.TRUE) {
        if (n instanceof And nested) {
          normalized.addAll(nested.children()); // 扁平化嵌套 AND
        } else {
          normalized.add(n);
        }
      }
      // 跳过 TRUE,因为 AND TRUE 是恒等操作
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

  /// 规范化 OR 表达式:扁平化、短路求值、去重。
  ///
  /// 优化规则:
  ///
  /// - 发现 TRUE → 整个表达式为 TRUE (短路)
  ///   - 移除所有 FALSE 子节点
  ///   - 扁平化嵌套 OR (OR(OR(a,b),c) → OR(a,b,c))
  ///   - 去重相同的子表达式
  ///   - 空列表 → FALSE,单元素 → 该元素本身
  ///
  /// @param orExpr OR 表达式
  /// @return 规范化后的表达式
  private Expr normalizeOr(Or orExpr) {
    List<Expr> normalized = new ArrayList<>();
    boolean hasTrue = false;
    for (Expr child : orExpr.children()) {
      Expr n = normalizeNode(child);
      if (n == Const.TRUE) {
        hasTrue = true;
        break; // 短路:OR 遇到 TRUE 即为 TRUE
      }
      if (n != Const.FALSE) {
        if (n instanceof Or nested) {
          normalized.addAll(nested.children()); // 扁平化嵌套 OR
        } else {
          normalized.add(n);
        }
      }
      // 跳过 FALSE,因为 OR FALSE 是恒等操作
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

  /// 规范化 NOT 表达式:双重否定消除和常量翻转。
  ///
  /// 优化规则:
  ///
  /// - NOT TRUE → FALSE
  ///   - NOT FALSE → TRUE
  ///   - NOT NOT x → x (双重否定消除)
  ///
  /// @param notExpr NOT 表达式
  /// @return 规范化后的表达式
  private Expr normalizeNot(Not notExpr) {
    Expr child = normalizeNode(notExpr.child());
    if (child instanceof Const constant) {
      return constant == Const.TRUE ? Const.FALSE : Const.TRUE;
    }
    if (child instanceof Not nested) {
      return normalizeNode(nested.child()); // 双重否定消除
    }
    return Exprs.not(child);
  }

  /// 去重:保持顺序的同时移除重复的表达式。
  ///
  /// @param expressions 表达式列表
  /// @return 去重后的表达式列表
  private List<Expr> dedupe(List<Expr> expressions) {
    Set<Expr> ordered = new LinkedHashSet<>(expressions);
    return new ArrayList<>(ordered);
  }
}
