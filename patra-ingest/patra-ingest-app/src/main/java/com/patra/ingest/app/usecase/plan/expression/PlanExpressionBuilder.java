package com.patra.ingest.app.usecase.plan.expression;

import cn.hutool.core.collection.CollUtil;
import com.patra.expr.And;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.canonical.ExprCanonicalSnapshot;
import com.patra.expr.canonical.ExprCanonicalizer;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Plan business expression builder, responsible for producing a canonical expression from the
 * trigger norm and the provenance configuration.
 *
 * <p>Used during plan assembly; can be extended to plug in external/custom rules.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PlanExpressionBuilder {

  /**
   * Construct the plan-level expression descriptor (original expression, canonical JSON, and hash).
   *
   * @param norm trigger norm
   * @param configSnapshot provenance configuration snapshot
   * @return plan expression descriptor
   */
  public PlanExpressionDescriptor build(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    log.debug(
        "Building plan expression for provenance [{}] operation [{}]",
        norm.provenanceCode(),
        norm.operationCode());

    Expr businessExpr = buildBusinessExpression(norm, configSnapshot);
    ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(businessExpr);

    log.debug(
        "Canonicalized plan expression for provenance [{}] operation [{}]: hash={}, json length={}",
        norm.provenanceCode(),
        norm.operationCode(),
        snapshot.hash(),
        snapshot.canonicalJson().length());

    return new PlanExpressionDescriptor(businessExpr, snapshot.canonicalJson(), snapshot.hash());
  }

  /** Build the business expression for the plan. */
  private Expr buildBusinessExpression(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    log.debug("Building plan business expression, operation={}", norm.operationCode());

    // Start with internal constraints derived from the trigger norm and configuration
    List<Expr> constraints = buildBusinessConstraints(norm, configSnapshot);
    Expr external = buildExternalConditionsExpr(norm);
    if (external != null) {
      if (external instanceof And(List<Expr> children)) {
        constraints.addAll(children);
      } else {
        constraints.add(external);
      }
    }

    if (CollUtil.isEmpty(constraints)) {
      return Exprs.constTrue();
    }
    if (constraints.size() == 1) {
      return constraints.getFirst();
    }
    return Exprs.and(constraints);
  }

  /** Reserved hook for external condition composition (e.g., admin-configured rules). */
  private Expr buildExternalConditionsExpr(PlanTriggerNorm norm) {
    return null;
  }

  /** Build base constraints. */
  private List<Expr> buildBusinessConstraints(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    List<Expr> constraints = new ArrayList<>();
    if (norm.isUpdate()) {
      constraints.addAll(buildUpdateBusinessConstraints(norm, configSnapshot));
    }
    return constraints;
  }

  /** Build constraints specific to UPDATE operations. */
  private List<Expr> buildUpdateBusinessConstraints(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    return new ArrayList<>();
  }
}
