package com.patra.starter.expr.compiler.normalize;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

/**
 * Expr 规范化：不改变语义（除等价折叠），为后续校验/渲染/切片提供稳定形态。
 * 建议顺序：结构化简 → 布尔代数最小化 → 排序/去重。
 */
public interface ExprNormalizer {

    /**
     * @param expr     原始表达式（不可变）
     * @param snapshot 规则快照（可用于字段粒度、datetype 决策；本雏形未使用）
     * @param strict   严格模式（本雏形未使用；留作值裁剪、模糊修正时参考）
     * @return 规范化后的新 Expr（幂等；若无改动可返回原对象）
     */
    Expr normalize(Expr expr, ProvenanceSnapshot snapshot, boolean strict);
}
