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

/// Plan 业务表达式构建器 - 负责从触发规范和 Provenance 配置生成标准化的表达式
/// 
/// 在 Plan 组装阶段使用;可扩展以插入外部/自定义规则。
/// 
/// ### 职责
/// 
/// - 根据 {@link PlanTriggerNorm} 和 {@link ProvenanceConfigSnapshot} 构建业务约束表达式
///   - 调用 {@link ExprCanonicalizer} 生成标准化 JSON 和 Hash
///   - 预留外部条件组合钩子(如管理员配置的规则)
/// 
/// ### 表达式构建策略
/// 
/// - **内部约束**: 从 TriggerNorm 和 ConfigSnapshot 派生
///   - **外部条件**: 预留扩展点 {@link #buildExternalConditionsExpr}
///   - **组合逻辑**: 通过 `And` 合并所有约束
/// 
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class PlanExpressionBuilder {

  /// 构建 Plan 级别的表达式描述符(原始表达式、标准化 JSON 和 Hash)
/// 
/// @param norm 触发规范
/// @param configSnapshot Provenance 配置快照
/// @return Plan 表达式描述符
  public PlanExpressionDescriptor build(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    log.debug(
        "正在为 Provenance [{}] Operation [{}] 构建 Plan 表达式",
        norm.provenanceCode(),
        norm.operationCode());

    Expr businessExpr = buildBusinessExpression(norm, configSnapshot);
    ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(businessExpr);

    log.debug(
        "已标准化 Plan 表达式,Provenance [{}] Operation [{}]: hash={}, json 长度={}",
        norm.provenanceCode(),
        norm.operationCode(),
        snapshot.hash(),
        snapshot.canonicalJson().length());

    return new PlanExpressionDescriptor(businessExpr, snapshot.canonicalJson(), snapshot.hash());
  }

  /// 构建 Plan 的业务表达式
  private Expr buildBusinessExpression(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    log.debug("正在构建 Plan 业务表达式,Operation={}", norm.operationCode());

    // 从触发规范和配置中派生内部约束
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

  /// 预留的外部条件组合钩子(例如管理员配置的规则)
  private Expr buildExternalConditionsExpr(PlanTriggerNorm norm) {
    return null;
  }

  /// 构建基础约束
  private List<Expr> buildBusinessConstraints(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    List<Expr> constraints = new ArrayList<>();
    if (norm.isUpdate()) {
      constraints.addAll(buildUpdateBusinessConstraints(norm, configSnapshot));
    }
    return constraints;
  }

  /// 构建 UPDATE 操作特定的约束
  private List<Expr> buildUpdateBusinessConstraints(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    return new ArrayList<>();
  }
}
