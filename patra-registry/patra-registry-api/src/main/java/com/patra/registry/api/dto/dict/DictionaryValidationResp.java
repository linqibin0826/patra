package com.patra.registry.api.dto.dict;

/// 字典引用的验证结果。
/// 
/// 字段说明:
/// 
/// @author linqibin
/// @since 0.1.0
public record DictionaryValidationResp(
    String typeCode, String itemCode, boolean valid, String errorMessage) {}
