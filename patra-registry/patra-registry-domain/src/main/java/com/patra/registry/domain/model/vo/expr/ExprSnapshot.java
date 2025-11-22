package com.patra.registry.domain.model.vo.expr;

import java.util.List;
import java.util.Objects;

/// 来源范围的聚合表达式快照。
/// 
/// 此记录在给定时间点捕获特定来源的所有表达式相关配置的完整、不可变快照。 它通常由表达式渲染引擎用于验证用户输入表达式、选择适当的渲染规则, 以及将标准键转换为提供者特定的参数名称。
/// 
/// 字段描述:
/// 
/// @author linqibin
/// @since 0.1.0
public record ExprSnapshot(
    List<ExprField> fields,
    List<ExprCapability> capabilities,
    List<ExprRenderRule> renderRules,
    List<ApiParamMapping> apiParamMappings) {
  /// 强制非空不变性的紧凑规范构造函数。
/// 
/// @throws NullPointerException 如果任何参数为 `null`
  public ExprSnapshot {
    Objects.requireNonNull(fields, "fields");
    Objects.requireNonNull(capabilities, "capabilities");
    Objects.requireNonNull(renderRules, "renderRules");
    Objects.requireNonNull(apiParamMappings, "apiParamMappings");
  }
}
