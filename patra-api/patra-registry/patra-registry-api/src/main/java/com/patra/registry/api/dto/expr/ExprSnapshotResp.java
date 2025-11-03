package com.patra.registry.api.dto.expr;

import java.util.List;

/**
 * 聚合表达式快照响应 DTO,组合字段元数据和映射。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>fields - 暴露给下游服务的字段字典
 *   <li>capabilities - 与每个字段关联的能力定义
 *   <li>renderRules - 描述输出模板化的渲染规则
 *   <li>apiParamMappings - 对齐提供商参数的 API 参数映射
 * </ol>
 *
 * @param fields 字段定义列表
 * @param capabilities 能力定义列表
 * @param renderRules 渲染规则列表
 * @param apiParamMappings API 参数映射列表
 * @author linqibin
 * @since 0.1.0
 */
public record ExprSnapshotResp(
    List<ExprFieldResp> fields,
    List<ExprCapabilityResp> capabilities,
    List<ExprRenderRuleResp> renderRules,
    List<ApiParamMappingResp> apiParamMappings) {}
