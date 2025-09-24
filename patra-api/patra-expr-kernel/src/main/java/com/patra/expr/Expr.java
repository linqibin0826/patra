package com.patra.expr;

/**
 * 抽象语法树（AST）根接口：仅表达“布尔逻辑 + 叶子原子”，不携带任何“平台/数据源”信息。
 * <p>约定：AST 不负责渲染与转义；渲染由 Translator 在“规范化/降级/能力检查”之后完成。</p>
 * Root interface of the expression abstract syntax tree used by the Papertrace platform.
 * <p>
 * 线程安全性：所有实现类型（record/enum）均为不可变对象；可安全在多线程间共享。
 * 资源与事务：不持有任何外部资源；不涉及事务边界。
 * 典型用法：通过 {@link Exprs} 工厂方法构造表达式树，再交由规范化与校验流程处理。
 *
 * @author linqibin
 * @since 0.1.0
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

