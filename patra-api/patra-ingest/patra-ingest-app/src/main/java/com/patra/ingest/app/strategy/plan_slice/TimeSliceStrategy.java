package com.patra.ingest.app.strategy.plan_slice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.app.model.PlanBusinessExpr;
import com.patra.ingest.app.strategy.plan_slice.model.SliceContext;
import com.patra.ingest.app.strategy.plan_slice.model.SliceDraft;
import com.patra.ingest.app.util.ExprHashUtil;
import com.patra.ingest.app.util.SliceSignatureUtil;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TimeSliceStrategy implements SliceStrategy {

    private static final Duration DEFAULT_STEP = Duration.ofHours(1);

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String code() {
        return "TIME";
    }

    @Override
    public List<SliceDraft> slice(SliceContext context) {
        List<SliceDraft> result = new ArrayList<>();
        if (context.window() == null || context.window().from() == null || context.window().to() == null) return result;
        // 解析时间字段：优先 windowOffset.offsetFieldName (仅当 offsetType=DATE)，其次 windowOffset.defaultDateFieldName；无法解析直接终止
        String timeField = resolveTimeField(context.configSnapshot());
        if (timeField == null) {
            return result; // TODO 无法确定时间字段：终止（上层将标记失败）
        }
        Instant from = context.window().from();
        Instant to = context.window().to();
        if (!from.isBefore(to)) return result;
        Duration step = DEFAULT_STEP;
        if (context.norm().step() != null && !context.norm().step().isBlank()) {
            try {
                step = Duration.parse(context.norm().step());
            } catch (Exception e) {
                // 保留默认步长
            }
        }
        Instant cursor = from;
        int index = 1;
        PlanBusinessExpr planExpr = context.planExpr();
        while (cursor.isBefore(to)) {
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) upper = to;
            String specJson = buildSpecJson(context, cursor, upper);
            Expr timeConstraint = buildTimeWindowConstraint(timeField, cursor, upper);
            Expr combined = Exprs.and(List.of(planExpr.expr(), timeConstraint));
            String combinedJson = Exprs.toJson(combined);
            String combinedHash = ExprHashUtil.sha256Hex(combinedJson);
            String signatureHash = SliceSignatureUtil.signatureHash(objectMapper, specJson);
            result.add(new SliceDraft(
                    index,
                    signatureHash,
                    specJson,
                    combined,
                    combinedJson,
                    combinedHash,
                    cursor,
                    upper));
            cursor = upper;
            index++;
        }
        return result;
    }

    /**
     * 构建时间窗口约束表达式
     */
    private Expr buildTimeWindowConstraint(String field, Instant from, Instant to) {
        return Exprs.rangeDateTime(field, from, to);
    }

    private String resolveTimeField(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) return null;
        ProvenanceConfigSnapshot.WindowOffsetConfig w = snapshot.windowOffset();
        if (w == null) return null;
        if (w.offsetTypeCode() != null && w.offsetTypeCode().equalsIgnoreCase("DATE") && w.offsetFieldName() != null && !w.offsetFieldName().isBlank()) {
            return w.offsetFieldName();
        }
        if (w.defaultDateFieldName() != null && !w.defaultDateFieldName().isBlank()) {
            return w.defaultDateFieldName();
        }
        return null; // 不回退
    }

    private String buildSpecJson(SliceContext context, Instant from, Instant to) {

        ProvenanceConfigSnapshot configSnapshot = context.configSnapshot();
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("strategy", code());
        // window
        ObjectNode window = root.putObject("window");
        window.put("from", from.toString());
        window.put("to", to.toString());
        ObjectNode boundary = window.putObject("boundary");
        boundary.put("from", "CLOSED");
        boundary.put("to", "OPEN");
        window.put("timezone", configSnapshot.provenance().timezoneDefault());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"strategy\":\"" + code() + "\"}";
        }
    }
}
