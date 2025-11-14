package com.patra.starter.expr.compiler.model;

import com.patra.expr.Expr;
import java.util.Map;
import java.util.Objects;

/**
 * 表达式编译结果。
 *
 * <p>包含渲染的查询文本、参数映射、规范化表达式、验证报告、快照引用和渲染跟踪。
 *
 * @param query 渲染的查询文本
 * @param params 参数映射(提供商参数名 -> 值)
 * @param normalized 规范化后的表达式
 * @param report 验证报告
 * @param snapshot 快照引用
 * @param trace 渲染跟踪(仅在启用跟踪时提供)
 * @author linqibin
 * @since 0.1.0
 */
public record CompileResult(
    String query,
    Map<String, String> params,
    Expr normalized,
    ValidationReport report,
    SnapshotRef snapshot,
    RenderTrace trace) {
  public CompileResult {
    query = query == null ? "" : query;
    params = params == null ? Map.of() : Map.copyOf(params);
    report = report == null ? ValidationReport.empty() : report;
    Objects.requireNonNull(snapshot, "snapshot");
  }
}
