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
    <R> R accept(ExprVisitor<R> visitor);

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

}

