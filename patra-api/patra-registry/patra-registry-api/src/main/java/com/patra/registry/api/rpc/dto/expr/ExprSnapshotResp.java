package com.patra.registry.api.rpc.dto.expr;

import java.util.List;

/**
 * Aggregated snapshot DTO combining expression field metadata and mappings.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>fields - field dictionary exposed to downstream services</li>
 *   <li>capabilities - capability definitions linked to each field</li>
 *   <li>renderRules - render rules describing output templating</li>
 *   <li>apiParamMappings - API parameter mappings aligning provider params</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprSnapshotResp(
        List<ExprFieldResp> fields,
        List<ExprCapabilityResp> capabilities,
        List<ExprRenderRuleResp> renderRules,
        List<ApiParamMappingResp> apiParamMappings
) {
}
