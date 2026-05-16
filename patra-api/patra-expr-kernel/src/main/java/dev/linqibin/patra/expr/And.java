package dev.linqibin.patra.expr;

import java.util.List;
import java.util.Objects;

/// 逻辑合取表达式。
///
/// 表示多个表达式的与逻辑结合。
public record And(List<Expr> children) implements Expr {

  /// 规范构造器,强制执行 AND 表达式的验证规则。
  ///
  /// 验证规则:
  ///
  /// - children 不能为 null
  /// - children 不能包含 null 元素
  /// - 创建不可变副本
  ///
  /// @throws IllegalArgumentException 如果 children 包含 null 元素
  public And {
    Objects.requireNonNull(children, "children");
    if (children.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("AND 表达式不能包含空的子表达式");
    }
    children = List.copyOf(children);
  }

  /// 接受访问者访问此 AND 表达式。
  ///
  /// @param visitor 表达式访问者
  /// @param <R> 访问者返回类型
  /// @return 访问者返回的结果
  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitAnd(this);
  }
}
