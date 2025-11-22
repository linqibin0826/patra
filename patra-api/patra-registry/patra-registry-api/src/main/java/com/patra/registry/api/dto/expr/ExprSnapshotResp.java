package com.patra.registry.api.dto.expr;

import java.util.List;

/// 聚合表达式快照响应 DTO,组合字段元数据和映射。
///
/// 字段说明:
///
/// @param fields 字段定义列表
/// @param capabilities 能力定义列表
/// @param renderRules 渲染规则列表
/// @param apiParamMappings API 参数映射列表
/// @author linqibin
/// @since 0.1.0
public record ExprSnapshotResp(
    List<ExprFieldResp> fields,
    List<ExprCapabilityResp> capabilities,
    List<ExprRenderRuleResp> renderRules,
    List<ApiParamMappingResp> apiParamMappings) {}
