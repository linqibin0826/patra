package com.patra.registry.api.dto.expr;

import java.util.List;

/**
 * Aggregated expression snapshot combining field metadata and mappings.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>fields - field dictionary exposed to downstream services
 *   <li>capabilities - capability definitions linked to each field
 *   <li>renderRules - render rules describing output templating
 *   <li>apiParamMappings - API parameter mappings aligning provider params
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprSnapshotResp(
    List<ExprFieldResp> fields,
    List<ExprCapabilityResp> capabilities,
    List<ExprRenderRuleResp> renderRules,
    List<ApiParamMappingResp> apiParamMappings) {}
