package com.patra.expr;


/**
 * 布尔非（NOT）。
 * <p>语义：对子表达式求逻辑否定。</p>
 * <p>规范化：NOT 的下推（得到 NNF）在 {@link ExprNormalizer} 中完成，仅保留于叶子或常量之上。</p>
 */
public record Not(Expr child) implements Expr {
}
