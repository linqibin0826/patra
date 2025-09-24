package com.patra.ingest.app.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.value.PlannerWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TimeSliceStrategy implements SliceStrategy {

    private static final Duration DEFAULT_STEP = Duration.ofHours(1);

    @Override
    public String code() {
        return "TIME";
    }

    @Override
    public List<PlanSliceAggregate> slice(PlanTriggerNorm norm,
                                          PlannerWindow window,
                                          ExprPlanArtifacts exprArtifacts) {
        List<PlanSliceAggregate> result = new ArrayList<>();
        if (window == null || window.from() == null || window.to() == null) return result;
        Instant from = window.from();
        Instant to = window.to();
        if (!from.isBefore(to)) return result;
        Duration step = DEFAULT_STEP;
        if (norm.step() != null && !norm.step().isBlank()) {
            try {
                step = Duration.parse(norm.step());
            } catch (Exception e) {
                // 保留默认步长
            }
        }
        Instant cursor = from;
        int index = 1;
        while (cursor.isBefore(to)) {
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) upper = to;
            String specJson = "{\"type\":\"TIME\",\"from\":\"" + cursor + "\",\"to\":\"" + upper + "\"}";
            
            // 构建 Slice 表达式：Plan业务表达式 AND 时间窗口约束
            String sliceExprHash = buildSliceExpressionHash(cursor, upper, exprArtifacts);
            String sliceExprSnapshot = buildSliceExpressionSnapshot(cursor, upper, exprArtifacts);
            
            result.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    index,
                    specJson, // 暂用 specJson 作为签名原料（后续 canonical hash）
                    specJson,
                    sliceExprHash,
                    sliceExprSnapshot));
            cursor = upper;
            index++;
        }
        return result;
    }
    
    /**
     * 构建 Slice 表达式哈希
     * Slice 表达式 = Plan业务表达式 AND 时间窗口约束
     */
    private String buildSliceExpressionHash(Instant from, Instant to, ExprPlanArtifacts exprArtifacts) {
        try {
            // 1. 获取 Plan 业务表达式快照
            String planExprSnapshot = exprArtifacts.exprProtoSnapshotJson();
            
            // 2. 构建时间窗口约束表达式
            Expr timeConstraint = buildTimeWindowConstraint(from, to);
            String timeConstraintSnapshot = serializeExprToJson(timeConstraint);
            
            // 3. 组合表达式：Plan业务表达式 AND 时间约束
            String combinedSnapshot = combineExpressions(planExprSnapshot, timeConstraintSnapshot);
            
            return Integer.toHexString(combinedSnapshot.hashCode());
        } catch (Exception e) {
            // 发生异常时回退到原有逻辑
            return exprArtifacts.exprProtoHash();
        }
    }
    
    /**
     * 构建 Slice 表达式快照
     * Slice 表达式 = Plan业务表达式 AND 时间窗口约束
     */
    private String buildSliceExpressionSnapshot(Instant from, Instant to, ExprPlanArtifacts exprArtifacts) {
        try {
            // 1. 获取 Plan 业务表达式快照
            String planExprSnapshot = exprArtifacts.exprProtoSnapshotJson();
            
            // 2. 构建时间窗口约束表达式
            Expr timeConstraint = buildTimeWindowConstraint(from, to);
            String timeConstraintSnapshot = serializeExprToJson(timeConstraint);
            
            // 3. 组合表达式：Plan业务表达式 AND 时间约束
            return combineExpressions(planExprSnapshot, timeConstraintSnapshot);
        } catch (Exception e) {
            // 发生异常时回退到原有逻辑
            return exprArtifacts.exprProtoSnapshotJson();
        }
    }
    
    /**
     * 构建时间窗口约束表达式
     */
    private Expr buildTimeWindowConstraint(Instant from, Instant to) {
        // 构建 RANGE(updated_at, from, to) 表达式
        return Exprs.rangeDateTime("updated_at", from, to);
    }
    
    /**
     * 组合两个表达式为 AND 表达式
     */
    private String combineExpressions(String planExprSnapshot, String timeConstraintSnapshot) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        
        // 反序列化两个表达式
        Expr planExpr = mapper.readValue(planExprSnapshot, Expr.class);
        Expr timeExpr = mapper.readValue(timeConstraintSnapshot, Expr.class);
        
        // 构建 AND 表达式
        Expr combinedExpr = Exprs.and(List.of(planExpr, timeExpr));
        
        // 序列化组合表达式
        return mapper.writeValueAsString(combinedExpr);
    }
    
    /**
     * 序列化表达式为 JSON
     */
    private String serializeExprToJson(Expr expr) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(expr);
    }
}
