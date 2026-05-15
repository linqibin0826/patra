package dev.linqibin.patra.ingest.app.usecase.plan.slicer;

import cn.hutool.core.util.StrUtil;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import dev.linqibin.commons.json.JsonNormalizationException;
import dev.linqibin.commons.json.JsonNormalizer;
import dev.linqibin.commons.json.JsonNormalizerResult;
import dev.linqibin.commons.util.HashUtils;
import dev.linqibin.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import dev.linqibin.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import dev.linqibin.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import dev.linqibin.patra.ingest.domain.model.enums.SliceStrategy;
import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/// 时间窗口切片策略(应用层·策略)
///
/// 使用固定步长将上游规划窗口 [from, to) 拆分为多个半开子窗口; 每个子窗口与业务表达式配对形成独立的 Slice。
///
/// 设计要点:
///
/// - 步长配置:优先使用触发上下文中的标准化步长(ISO-8601 Duration);无效时回退到默认 1 小时。
///   - 时间字段解析:offsetFieldKey(DATE 模式) > windowDateFieldKey。
///   - 幂等性:构建规范化 JSON 并取 sha256;重复规划产生相同签名。
///   - 边界处理:如果最后一个切片短于步长,则对齐到窗口结束。
///   - 复杂度:O(n),n = ceil((to - from) / step)。
///
/// 返回空列表的情况:窗口缺失;from >= to;或时间字段无法解析。
@Slf4j
@Component
public class TimeSlicePlanner implements SlicePlanner {

  /// 默认切片步长(1 小时)
  private static final Duration DEFAULT_STEP = Duration.ofHours(1);

  @Override
  public SliceStrategy code() {
    return SliceStrategy.TIME;
  }

  @Override
  public List<SlicePlan> slice(SlicePlanningContext context) {
    // 初始化结果以保持排序稳定,即使提前返回
    List<SlicePlan> result = new ArrayList<>();
    if (context.window() == null
        || context.window().from() == null
        || context.window().to() == null) {
      log.warn("跳过时间切片,因为规划窗口缺失: norm={}, window={}", context.norm(), context.window());
      return result;
    }

    // 解析时间字段:优先 offsetFieldKey(DATE 模式),否则回退到 windowDateFieldKey
    String timeField = resolveTimeField(context.configSnapshot());
    if (timeField == null) {
      log.error(
          "无法从溯源快照解析时间字段, provenanceCode={}, operation={}",
          context.norm().provenanceCode(),
          context.norm().operationCode());
      return result;
    }

    Instant from = context.window().from();
    Instant to = context.window().to();
    if (!from.isBefore(to)) {
      log.warn("跳过时间切片,因为窗口不是前向的, from={} to={}", from, to);
      return result;
    }

    // 如果存在,使用 norm 中的自定义步长;否则回退到默认值
    Duration step = DEFAULT_STEP;
    if (StrUtil.isNotBlank(context.norm().step())) {
      try {
        step = Duration.parse(context.norm().step().trim());
      } catch (Exception e) {
        log.warn("步长格式无效,回退到默认值, stepString={}", context.norm().step(), e);
      }
    }

    log.debug(
        "开始 TIME 切片,溯源 [{}] 操作 [{}]: window=[{}, {}), step={}, timeField={}",
        context.norm().provenanceCode(),
        context.norm().operationCode(),
        from,
        to,
        step,
        timeField);

    Instant cursor = from;
    int index = 1;
    PlanExpressionDescriptor planExpr = context.planExpression();
    while (cursor.isBefore(to)) {
      // 计算当前切片上界;确保最后一个切片对齐到窗口结束
      Instant upper = cursor.plus(step);
      if (upper.isAfter(to)) {
        upper = to;
      }

      // 防止无限循环:确保游标可以前进(upper 必须在 cursor 之后)
      if (!cursor.isBefore(upper)) {
        log.warn("停止时间切片:游标无法前进, cursor={}, upper={}, to={}", cursor, upper, to);
        break;
      }

      // 构建切片规格并生成稳定签名
      JsonNormalizerResult specNormalized = buildSpec(context, cursor, upper);
      String specJson = specNormalized.getCanonicalJson();
      String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

      // 将 Plan 表达式与时间窗口约束组合
      Expr timeConstraint = buildTimeWindowConstraint(timeField, cursor, upper);
      Expr combined = Exprs.and(List.of(planExpr.expr(), timeConstraint));

      result.add(new SlicePlan(index, signatureHash, specJson, combined));

      log.debug(
          "时间切片准备完成, sliceNo={}, from={}, to={}, hash={}", index, cursor, upper, signatureHash);

      cursor = upper;
      index++;
    }

    log.debug(
        "TIME 切片完成,溯源 [{}] 操作 [{}]: 生成 {} 个切片",
        context.norm().provenanceCode(),
        context.norm().operationCode(),
        result.size());

    return result;
  }

  /// 构建时间窗口约束表达式。半开区间语义:from 包含,to 排除。
  ///
  /// @param field 时间字段名
  /// @param from 切片开始(包含)
  /// @param to 切片结束(排除)
  /// @return 范围表达式
  private Expr buildTimeWindowConstraint(String field, Instant from, Instant to) {
    return Exprs.rangeDateTime(field, from, to);
  }

  /// 从配置快照解析时间字段。优先级:DATE 模式 offsetFieldKey > windowDateFieldKey。
  ///
  /// @param snapshot 溯源/数据源配置快照
  /// @return 可用于范围过滤的字段名;无法解析时返回 null
  private String resolveTimeField(ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
    if (windowOffset == null) {
      return null;
    }
    if (StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "DATE")
        && StrUtil.isNotBlank(windowOffset.offsetFieldKey())) {
      return windowOffset.offsetFieldKey();
    }
    if (StrUtil.isNotBlank(windowOffset.windowDateFieldKey())) {
      return windowOffset.windowDateFieldKey();
    }
    return null;
  }

  /// 构建切片规格 JSON 并规范化。字段:strategy、window(from/to + boundary + timezone)。 规范化失败时,回退到最小 JSON 以保持哈希可用。
  ///
  /// @param context 切片上下文
  /// @param from 窗口开始
  /// @param to 窗口结束
  /// @return 规范化结果(规范化 JSON + 哈希材料)
  private JsonNormalizerResult buildSpec(SlicePlanningContext context, Instant from, Instant to) {
    ProvenanceConfigSnapshot configSnapshot = context.configSnapshot();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("strategy", code().getCode());

    // 构建窗口节点,包含时区和边界语义用于审计
    ObjectNode window = root.putObject("window");
    window.put("from", from.toString());
    window.put("to", to.toString());
    ObjectNode boundary = window.putObject("boundary");
    boundary.put("from", "CLOSED");
    boundary.put("to", "OPEN");

    String timezone =
        configSnapshot != null && configSnapshot.provenance() != null
            ? StrUtil.blankToDefault(configSnapshot.provenance().timezoneDefault(), "UTC")
            : "UTC";
    window.put("timezone", timezone);

    try {
      return JsonNormalizer.normalizeDefault(root);
    } catch (JsonNormalizationException ex) {
      log.error("规范化切片规格失败,回退到最小载荷, from={}, to=", from, to, ex);
      String fallback = "{\"strategy\":\"" + code().getCode() + "\"}";
      try {
        return JsonNormalizer.normalizeDefault(fallback);
      } catch (JsonNormalizationException ignored) {
        throw ex;
      }
    }
  }
}
