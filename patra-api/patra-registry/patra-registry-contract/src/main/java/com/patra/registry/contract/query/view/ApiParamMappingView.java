package com.patra.registry.contract.query.view;

/**
 * API 参数映射读侧视图。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMappingView(
        String operation,
        String stdKey,
        String providerParam,
        String transform,
        String notes
) {}
