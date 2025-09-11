package com.patra.registry.api.rpc.dto;

/**
 * API 参数映射对外 DTO。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMappingApiResp(
        String operation,
        String stdKey,
        String providerParam,
        String transform,
        String notes
) {}
