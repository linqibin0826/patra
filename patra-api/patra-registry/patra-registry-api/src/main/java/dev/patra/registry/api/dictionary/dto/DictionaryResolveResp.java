package dev.patra.registry.api.dictionary.dto;

import java.util.List;

/// 字典解析批量响应。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryResolveResp(
    String typeCode, String sourceStandard, List<DictionaryResolveItemResp> items) {}
