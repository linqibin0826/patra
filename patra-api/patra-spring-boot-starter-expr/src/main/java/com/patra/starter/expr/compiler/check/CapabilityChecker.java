package com.patra.starter.expr.compiler.check;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.List;

/**
 * 能力检查器,验证表达式是否符合数据源的能力限制。
 *
 * <p>例如,某些数据源可能不支持 NOT 操作符或特定的字段约束。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CapabilityChecker {
  /**
   * 检查表达式能力。
   *
   * @param expression 待检查的表达式
   * @param snapshot 溯源快照
   * @param strictMode 严格模式
   * @return 问题列表
   */
  List<Issue> check(Expr expression, ProvenanceSnapshot snapshot, boolean strictMode);
}
