package com.patra.registry.api.dto.dict;

/// 字典类型元数据,通过内部 HTTP API 暴露。
/// 
/// 字段说明:
/// 
/// @author linqibin
/// @since 0.1.0
public record DictionaryTypeResp(
    String typeCode,
    String typeName,
    String description,
    int enabledItemCount,
    boolean hasDefault) {}
