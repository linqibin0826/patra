package com.patra.registry.api.dto.dict;

/// 字典解析项响应。
///
/// 表示单个原始值的解析结果。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryResolveItemResp(
    String rawValue, String resolvedCode, String resolvedName, String status) {}
