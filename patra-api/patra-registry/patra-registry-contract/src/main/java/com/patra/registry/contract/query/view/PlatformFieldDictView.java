package com.patra.registry.contract.query.view;

/**
 * 平台字段字典读侧视图（只读投影）。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PlatformFieldDictView(
        String fieldKey,
        String dataType,
        String cardinality,
        Boolean isDate,
        String datetype
) {}
