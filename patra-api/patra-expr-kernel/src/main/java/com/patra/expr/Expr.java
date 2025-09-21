package com.patra.expr;

/**
 * Root interface of the expression abstract syntax tree used by the Papertrace platform.
 * <p>
 * Every concrete node is immutable and thread-safe.  Consumers are expected to
 * traverse the tree via the provided {@link Visitor} interface instead of relying on
 * reflection or implementation details.
 * </p>
 */
public sealed interface Expr permits And, Or, Not, Const, Atom {

    /**
     * Accept a visitor and delegate to the corresponding handler.
     */
    <R> R accept(Visitor<R> visitor);

    /**
     * Convenience shortcut for {@code this == Const.TRUE}.
     */
    default boolean isConstTrue() {
        return this == Const.TRUE;
    }

    /**
     * Convenience shortcut for {@code this == Const.FALSE}.
     */
    default boolean isConstFalse() {
        return this == Const.FALSE;
    }

    /**
     * Base visitor for expression nodes.
     */
    interface Visitor<R> {
        R visitAnd(And andExpr);

        R visitOr(Or orExpr);

        R visitNot(Not notExpr);

        R visitConst(Const constantExpr);

        R visitAtom(Atom atomExpr);
    }

    /**
     * Visitor that does not need to produce a return value.
     */
    interface VoidVisitor extends Visitor<Void> {
        @Override
        default Void visitAnd(And andExpr) {
            visit(andExpr);
            return null;
        }

        @Override
        default Void visitOr(Or orExpr) {
            visit(orExpr);
            return null;
        }

        @Override
        default Void visitNot(Not notExpr) {
            visit(notExpr);
            return null;
        }

        @Override
        default Void visitConst(Const constantExpr) {
            visit(constantExpr);
            return null;
        }

        @Override
        default Void visitAtom(Atom atomExpr) {
            visit(atomExpr);
            return null;
        }

        void visit(Expr expr);
    }
}
