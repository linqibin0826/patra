package dev.linqibin.patra.registry.adapter.rest.dictionary.response;

/// 单个字典项的响应 DTO。
///
/// @param code 字典项代码（如 "CN"、"US"）
/// @param name 字典项默认名称（英文）
/// @param label 本地化标签（由 labelStandard 决定），不传 labelStandard 时为 null
/// @param displayOrder 显示排序
/// @author linqibin
/// @since 0.1.0
public record DictionaryItemResponse(String code, String name, String label, int displayOrder) {}
