package com.patra.expr;

/**
 * 访问者接口：与表达式模型解耦，便于在内核外扩展实现（如 JSON 序列化、渲染器等）。
 * 所有实现要求线程安全；建议实现为无状态或只读状态对象。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExprVisitor<R> {
    R visitAnd(And andExpr);

    R visitOr(Or orExpr);

    R visitNot(Not notExpr);

    R visitConst(Const constantExpr);

    R visitAtom(Atom atomExpr);

    abstract class NoReturn implements ExprVisitor<java.lang.Void> {
        @Override public final java.lang.Void visitAnd(And andExpr) { visit(andExpr); return null; }
        @Override public final java.lang.Void visitOr(Or orExpr) { visit(orExpr); return null; }
        @Override public final java.lang.Void visitNot(Not notExpr) { visit(notExpr); return null; }
        @Override public final java.lang.Void visitConst(Const constantExpr) { visit(constantExpr); return null; }
        @Override public final java.lang.Void visitAtom(Atom atomExpr) { visit(atomExpr); return null; }
        protected abstract void visit(Expr expr);
    }
}
