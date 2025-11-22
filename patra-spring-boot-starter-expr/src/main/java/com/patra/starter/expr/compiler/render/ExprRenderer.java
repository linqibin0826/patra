package com.patra.starter.expr.compiler.render;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.List;
import java.util.Map;

/// 表达式渲染器，将 Expr 渲染为提供方特定的查询字符串和参数。
public interface ExprRenderer {
  /// 渲染表达式。
/// 
/// @param expression 要渲染的表达式
/// @param snapshot Provenance 快照
/// @param traceEnabled 是否启用跟踪
/// @return 渲染结果
  RenderOutcome render(Expr expression, ProvenanceSnapshot snapshot, boolean traceEnabled);

  /// 渲染结果。
/// 
/// @param query 渲染的查询字符串
/// @param params 标准键参数映射
/// @param warnings 渲染警告列表
/// @param trace 渲染跟踪（如果启用）
  record RenderOutcome(
      String query, Map<String, String> params, List<Issue> warnings, RenderTrace trace) {}
}
