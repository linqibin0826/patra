package com.patra.registry.api.dto.dict;

/// 字典项元数据,暴露给子系统客户端。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryItemResp(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled) {}
