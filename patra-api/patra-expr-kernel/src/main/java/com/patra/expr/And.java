package com.patra.expr;

import java.util.List;

/**
 * 布尔与（AND）。
 * <p>语义：所有子表达式皆为真时整体为真；存在任一子表达式为假时整体为假。</p>
 */
public record And(List<Expr> children) implements Expr {
}
