package com.patra.ingest.app.usecase.plan.slicer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于时间窗口的切片策略（Application Layer · Policy）。
 * <p>
 * 将上游计划窗口 [from, to) 按固定步长切分为若干半开区间子窗口；每个子窗口融合业务表达式生成独立 Slice。
 * </p>
 * <p>设计要点：
 * <ul>
 *   <li>步长配置：优先使用规范化上下文中的 step（ISO-8601 Duration），非法则回退默认 1h。</li>
 *   <li>时间字段解析：offsetFieldName (DATE 模式) > defaultDateFieldName。</li>
 *   <li>幂等性：对规格 JSON 规范化后取 sha256，重复规划得到相同签名。</li>
 *   <li>边界：最后一个切片不足步长直接对齐窗口终点。</li>
 *   <li>复杂度：O(n)，n = ceil((to-from)/step)。</li>
 * </ul>
 * </p>
 * <p>失败返回空列表：窗口缺失；from>=to；无法解析时间字段。</p>
 */
@Slf4j
@Component
public class TimeSlicePlanner implements SlicePlanner {

    /**
     * 默认切片步长（1 小时）。
     */
    private static final Duration DEFAULT_STEP = Duration.ofHours(1);

    @Override
    public SliceStrategy code() {
        return SliceStrategy.TIME;
    }

    @Override
    public List<SlicePlan> slice(SlicePlanningContext context) {
        // 初始化返回集合，保持顺序稳定
        List<SlicePlan> result = new ArrayList<>();
        if (context.window() == null || context.window().from() == null || context.window().to() == null) {
            log.warn("[INGEST][APP] Skip time slicing because planning window is missing: norm={}, window={}.",
                    context.norm(), context.window());
            return result;
        }

        // 解析时间字段：优先使用 offsetFieldName（仅 DATE 模式），否则回退到 defaultDateFieldName
        String timeField = resolveTimeField(context.configSnapshot());
        if (timeField == null) {
            log.error("[INGEST][APP] Cannot resolve time field from provenance snapshot, provenanceCode={}, operation={}",
                    context.norm().provenanceCode(),
                    context.norm().operationCode());
            return result;
        }

        Instant from = context.window().from();
        Instant to = context.window().to();
        if (!from.isBefore(to)) {
            log.warn("[INGEST][APP] Skip time slicing because window is not forward, from={} to={}.", from, to);
            return result;
        }

        // 使用 norm 中自定义步长，否则回落到默认步长
        Duration step = DEFAULT_STEP;
        if (StrUtil.isNotBlank(context.norm().step())) {
            try {
                step = Duration.parse(context.norm().step().trim());
            } catch (Exception e) {
                log.warn("[INGEST][APP] Invalid step format, fallback to default, stepString={}.", context.norm().step(), e);
            }
        }

        Instant cursor = from;
        int index = 1;
        PlanExpressionDescriptor planExpr = context.planExpression();
        while (cursor.isBefore(to)) {
            // 计算当前切片的上界，确保最后一个切片对齐到窗口终点
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) {
                upper = to;
            }

            // 构造切片规格并生成稳定签名
            JsonNormalizer.Result specNormalized = buildSpec(context, cursor, upper);
            String specJson = specNormalized.getCanonicalJson();
            String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

            // 合并计划表达式与时间窗口约束
            Expr timeConstraint = buildTimeWindowConstraint(timeField, cursor, upper);
            Expr combined = Exprs.and(List.of(planExpr.expr(), timeConstraint));

            result.add(new SlicePlan(
                    index,
                    signatureHash,
                    specJson,
                    combined,
                    cursor,
                    upper));

            log.debug("[INGEST][APP] Time slice prepared, sliceNo={}, from={}, to={}, hash={}", index, cursor, upper, signatureHash);

            cursor = upper;
            index++;
        }
        return result;
    }

    /**
     * 构建时间窗口约束表达式。半开区间约定：from 含，to 开。
     *
     * @param field 时间字段名
     * @param from  切片起点（含）
     * @param to    切片终点（开）
     * @return 时间范围表达式
     */
    private Expr buildTimeWindowConstraint(String field, Instant from, Instant to) {
        return Exprs.rangeDateTime(field, from, to);
    }

    /**
     * 从配置快照中解析时间字段。优先级：DATE 模式 offsetFieldName > defaultDateFieldName。
     *
     * @param snapshot 来源配置快照
     * @return 可用于范围过滤的字段名，无法解析返回 null
     */
    private String resolveTimeField(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
        if (windowOffset == null) {
            return null;
        }
        if (StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "DATE")
                && StrUtil.isNotBlank(windowOffset.offsetFieldName())) {
            return windowOffset.offsetFieldName();
        }
        if (StrUtil.isNotBlank(windowOffset.defaultDateFieldName())) {
            return windowOffset.defaultDateFieldName();
        }
        return null;
    }

    /**
     * 构建切片规格 JSON，并执行规范化。字段：strategy、window(from/to + boundary + timezone)。
     * 规范化失败回退最小 JSON 保证哈希仍可用。
     *
     * @param context 切片上下文
     * @param from    切片起点
     * @param to      切片终点
     * @return 规范化结果（canonical JSON + 哈希素材）
     */
    private JsonNormalizer.Result buildSpec(SlicePlanningContext context, Instant from, Instant to) {
        ProvenanceConfigSnapshot configSnapshot = context.configSnapshot();
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("strategy", code().getCode());

        // 构造 window 节点，确保时区信息与边界语义可溯源
        ObjectNode window = root.putObject("window");
        window.put("from", from.toString());
        window.put("to", to.toString());
        ObjectNode boundary = window.putObject("boundary");
        boundary.put("from", "CLOSED");
        boundary.put("to", "OPEN");

        String timezone = configSnapshot != null && configSnapshot.provenance() != null
                ? StrUtil.blankToDefault(configSnapshot.provenance().timezoneDefault(), "UTC")
                : "UTC";
        window.put("timezone", timezone);

        try {
            return JsonNormalizer.normalizeDefault(root);
        } catch (JsonNormalizer.JsonNormalizationException ex) {
            log.error("[INGEST][APP] Failed to normalize slice spec, fallback to minimal payload, from={}, to={}", from, to, ex);
            String fallback = "{\"strategy\":\"" + code().getCode() + "\"}";
            try {
                return JsonNormalizer.normalizeDefault(fallback);
            } catch (JsonNormalizer.JsonNormalizationException ignored) {
                throw ex;
            }
        }
    }
}
