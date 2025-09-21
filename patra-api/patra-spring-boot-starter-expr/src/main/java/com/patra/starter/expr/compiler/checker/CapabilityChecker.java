package com.patra.starter.expr.compiler.checker;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.ExprCompiler.Issue;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.List;

/**
 * 能力校验器：基于规则快照对 Expr 做结构/能力约束校验。
 * 仅负责生成 Issue 列表；聚合为 ValidationReport 由上层完成。
 */
public interface CapabilityChecker {

    /**
     * @param expr      待校验表达式（已由调用方决定是否做规范化）
     * @param snapshot  规则快照（字段字典/能力矩阵）
     * @param provenance 数据源代码（仅用于上下文）
     * @param operation  operation（search/fetch/... 仅用于上下文）
     * @param strict    严格模式：遇到无法判断的模糊情况时是否直接报错
     * @return warnings + errors 的合并列表（由调用方按严重级别拆分）
     */
    List<Issue> check(Expr expr,
                      ProvenanceSnapshot snapshot,
                      ProvenanceCode provenance,
                      String operation,
                      boolean strict);
}
