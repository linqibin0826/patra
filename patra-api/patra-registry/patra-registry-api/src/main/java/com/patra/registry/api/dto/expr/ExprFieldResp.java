package com.patra.registry.api.dto.expr;

/// 暴露给 RPC 客户端的表达式字段定义。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record ExprFieldResp(
    String fieldKey,
    String displayName,
    String description,
    String dataTypeCode,
    String cardinalityCode,
    boolean exposable,
    boolean dateField) {}
