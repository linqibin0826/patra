package com.patra.expr;

/// 表达式访问者契约,与表达式模型解耦,使编解码器、渲染器和其他转换器可以在内核外部安全遍历表达式树。
/// 
/// 实现类应该是线程安全的,优先使用无状态或只读设计。
/// 
/// @param <R> 访问者返回类型
/// @author linqibin
/// @since 0.1.0
public interface ExprVisitor<R> {
  /// 访问 AND 表达式节点。
/// 
/// @param andExpr AND 表达式
/// @return 访问结果
  R visitAnd(And andExpr);

  /// 访问 OR 表达式节点。
/// 
/// @param orExpr OR 表达式
/// @return 访问结果
  R visitOr(Or orExpr);

  /// 访问 NOT 表达式节点。
/// 
/// @param notExpr NOT 表达式
/// @return 访问结果
  R visitNot(Not notExpr);

  /// 访问常量表达式节点。
/// 
/// @param constantExpr 常量表达式
/// @return 访问结果
  R visitConst(Const constantExpr);

  /// 访问原子表达式节点。
/// 
/// @param atomExpr 原子表达式
/// @return 访问结果
  R visitAtom(Atom atomExpr);

  /// 无返回值的访问者抽象类,简化无需返回值的访问者实现。
/// 
/// 子类只需实现 {@link #visit(Expr)} 方法,处理所有类型的表达式节点。
  abstract class NoReturn implements ExprVisitor<Void> {
    @Override
    public final Void visitAnd(And andExpr) {
      visit(andExpr);
      return null;
    }

    @Override
    public final Void visitOr(Or orExpr) {
      visit(orExpr);
      return null;
    }

    @Override
    public final Void visitNot(Not notExpr) {
      visit(notExpr);
      return null;
    }

    @Override
    public final Void visitConst(Const constantExpr) {
      visit(constantExpr);
      return null;
    }

    @Override
    public final Void visitAtom(Atom atomExpr) {
      visit(atomExpr);
      return null;
    }

    /// 访问任意表达式节点的通用方法。
/// 
/// @param expr 待访问的表达式
    protected abstract void visit(Expr expr);
  }
}
