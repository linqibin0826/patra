package com.patra.registry.api.rpc.dto.expr;

import java.util.List;

/**
 * Aggregated snapshot DTO containing fields, capabilities, render rules and API param mappings.
 */
public record ExprSnapshotResp(
        List<ExprFieldResp> fields,
        List<ExprCapabilityResp> capabilities,
        List<ExprRenderRuleResp> renderRules,
        List<ApiParamMappingResp> apiParamMappings
) {
}
