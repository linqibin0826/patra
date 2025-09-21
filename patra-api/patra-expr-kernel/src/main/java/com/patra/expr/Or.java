package com.patra.expr;


import java.util.List;

/**
 * 布尔或（OR）。
 * <p>语义：任一子表达式为真时整体为真；全部子表达式为假时整体为假。</p>
 * <p>规范化：子节点为空时，经 {@link ExprNormalizer} 规范化后等价于 {@link Const#FALSE}。</p>
 */
public record Or(List<Expr> children) implements Expr {
}
