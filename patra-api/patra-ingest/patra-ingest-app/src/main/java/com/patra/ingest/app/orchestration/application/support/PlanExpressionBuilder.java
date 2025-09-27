package com.patra.ingest.app.orchestration.application.support;

import cn.hutool.core.collection.CollUtil;
import com.patra.expr.And;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.canonical.ExprCanonicalizer;
import com.patra.expr.canonical.ExprCanonicalSnapshot;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Plan 业务表达式构建器，负责根据触发命令与来源配置生成 canonical 表达式。
 * <p>在计划装配阶段使用，可扩展外部自定义规则。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PlanExpressionBuilder {

    /**
     * 构造计划级表达式描述对象（含原始表达式、规范化 JSON 与哈希）。
     *
     * @param norm 触发规范
     * @param configSnapshot 来源配置快照
     * @return 计划表达式描述
     */
    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        Expr businessExpr = buildBusinessExpression(norm, configSnapshot);
        ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(businessExpr);
        return new PlanExpressionDescriptor(businessExpr, snapshot.canonicalJson(), snapshot.hash());
    }

    /**
     * 构建计划业务表达式。
     */
    private Expr buildBusinessExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        log.debug("Building plan business expression, operation={}", norm.operationCode());

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

    /**
     * 预留外部条件拼接入口，后续可接入管理员自定义规则。
     */
    private Expr buildExternalConditionsExpr(PlanTriggerNorm norm) {
        return null;
    }

    /**
     * 构建基础约束列表。
     */
    private List<Expr> buildBusinessConstraints(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        List<Expr> constraints = new ArrayList<>();
        if (norm.isUpdate()) {
            constraints.addAll(buildUpdateBusinessConstraints(norm, configSnapshot));
        }
        return constraints;
    }

    /**
     * 构建 UPDATE 操作相关约束。
     */
    private List<Expr> buildUpdateBusinessConstraints(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        return new ArrayList<>();
    }
}
