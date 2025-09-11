package com.patra.registry.api.rpc.dto;

/**
 * 平台字段字典对外 DTO。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PlatformFieldDictApiResp(
        String fieldKey,
        String dataType,
        String cardinality,
        Boolean isDate,
        String datetype
) {}
