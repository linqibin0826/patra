package com.patra.registry.domain.model.read.expr;

import java.util.List;
import java.util.Objects;

/// 聚合表达式快照查询视图。
/// 
/// 将字段定义、能力、渲染规则和API参数映射打包在一起的读优化投影。提供完整的表达式相关配置快照。
/// 
/// @author linqibin
/// @since 0.1.0
public record ExprSnapshotQuery(
    List<ExprFieldQuery> fields,
    List<ExprCapabilityQuery> capabilities,
    List<ExprRenderRuleQuery> renderRules,
    List<ApiParamMappingQuery> apiParamMappings) {
  public ExprSnapshotQuery {
    Objects.requireNonNull(fields, "fields");
    Objects.requireNonNull(capabilities, "capabilities");
    Objects.requireNonNull(renderRules, "renderRules");
    Objects.requireNonNull(apiParamMappings, "apiParamMappings");
  }
}
