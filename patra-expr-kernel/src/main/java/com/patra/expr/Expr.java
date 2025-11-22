package com.patra.expr;

/// 用于 Patra 平台的表达式抽象语法树 (AST) 根接口。
///
/// 该树仅使用不可变的叶节点建模布尔逻辑,故意避免任何平台或数据源特定的细节。 渲染和转义被委托给在规范化、降级和能力检查后运行的转换器。
///
/// 线程安全性: 所有实现(记录/枚举)都是不可变的,可以在线程间自由共享。 资源使用: 节点不持有外部资源,不参与事务边界。 典型用法: 通过 {@link Exprs}
/// 工厂方法构建表达式树,然后将其交给规范化和验证管道。
///
/// @author linqibin
/// @since 0.1.0
public sealed interface Expr permits And, Or, Not, Const, Atom {

  /// 接受提供的访问者并委托到匹配的处理器。
  ///
  /// @param visitor 要调用的访问者
  /// @param <R> 访问者返回类型
  /// @return 访问者结果
  <R> R accept(ExprVisitor<R> visitor);

  /// `this == Const.TRUE` 的便利快捷方式。
  ///
  /// @return 当该节点是布尔常量 TRUE 时返回 `true`
  default boolean isConstTrue() {
    return this == Const.TRUE;
  }

  /// `this == Const.FALSE` 的便利快捷方式。
  ///
  /// @return 当该节点是布尔常量 FALSE 时返回 `true`
  default boolean isConstFalse() {
    return this == Const.FALSE;
  }
}
