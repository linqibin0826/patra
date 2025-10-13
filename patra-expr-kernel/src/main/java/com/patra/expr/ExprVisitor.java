package com.patra.expr;

/**
 * Visitor contract decoupled from the expression model so that codecs, renderers, and other
 * translators can live outside the kernel while still traversing the tree safely. Implementations
 * are expected to be thread-safe; prefer stateless or read-only designs.
 *
 * @param <R> visitor return type
 */
public interface ExprVisitor<R> {
  R visitAnd(And andExpr);

  R visitOr(Or orExpr);

  R visitNot(Not notExpr);

  R visitConst(Const constantExpr);

  R visitAtom(Atom atomExpr);

  abstract class NoReturn implements ExprVisitor<java.lang.Void> {
    @Override
    public final java.lang.Void visitAnd(And andExpr) {
      visit(andExpr);
      return null;
    }

    @Override
    public final java.lang.Void visitOr(Or orExpr) {
      visit(orExpr);
      return null;
    }

    @Override
    public final java.lang.Void visitNot(Not notExpr) {
      visit(notExpr);
      return null;
    }

    @Override
    public final java.lang.Void visitConst(Const constantExpr) {
      visit(constantExpr);
      return null;
    }

    @Override
    public final java.lang.Void visitAtom(Atom atomExpr) {
      visit(atomExpr);
      return null;
    }

    protected abstract void visit(Expr expr);
  }
}
