package dev.linqibin.patra.ingest.app.usecase.plan.slicer;

import dev.linqibin.commons.json.JsonNormalizer;
import dev.linqibin.commons.json.JsonNormalizerResult;
import dev.linqibin.commons.util.HashUtils;
import dev.linqibin.patra.expr.Expr;
import dev.linqibin.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import dev.linqibin.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import dev.linqibin.patra.ingest.domain.model.enums.SliceStrategy;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 单切片策略(应用层·策略)
///
/// 当窗口不需要进一步分区或业务表达式已嵌入足够的过滤条件时使用(例如完整重放或由外部 ID 列表驱动)。 此策略恰好生成一个 sliceNo = 1 的切片,并重用上游 Plan
/// 表达式。保证:
///
/// - 幂等性:通过规范化 JSON 规格 + 哈希实现稳定签名。
///   - 最小开销:无循环;O(1) 复杂度。
///   - 窗口语义:如果上游提供了窗口,from/to 将被记录在切片规格中用于审计。
///
/// 边界情况:如果窗口为 null,切片仍返回单个项;调用方决定是否允许无窗口执行。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class SingleSlicePlanner implements SlicePlanner {

  @Override
  public SliceStrategy code() {
    return SliceStrategy.SINGLE;
  }

  @Override
  public List<SlicePlan> slice(SlicePlanningContext context) {
    // UPDATE / ID 驱动的场景:不在此处添加额外的窗口约束;尊重 Plan 的基础表达式
    Expr baseExpr = context.planExpression().expr();

    // 构建窗口规格 JSON,如果存在窗口信息则包含
    Map<String, Object> specMap = new java.util.HashMap<>();
    specMap.put("strategy", code().getCode());
    if (context.window() != null) {
      Map<String, String> windowMap = new java.util.HashMap<>();
      if (context.window().from() != null) {
        windowMap.put("from", context.window().from().toString());
      }
      if (context.window().to() != null) {
        windowMap.put("to", context.window().to().toString());
      }
      if (!windowMap.isEmpty()) {
        specMap.put("window", windowMap);
      }
    }

    JsonNormalizerResult specNormalized = JsonNormalizer.normalizeDefault(specMap);
    String specJson = specNormalized.getCanonicalJson();
    String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

    log.debug("单切片规划完成, 溯源={}, 哈希={}", context.norm().provenanceCode(), signatureHash);

    return List.of(new SlicePlan(1, signatureHash, specJson, baseExpr));
  }
}
