package com.patra.registry.adapter.rest.dictionary.response;

import java.util.List;

/// 字典项列表查询响应 DTO。
///
/// @param typeCode 字典类型代码（如 "country"、"language"）
/// @param labelStandard 请求的标签标准代码，未传时为 null
/// @param items 字典项列表
/// @author linqibin
/// @since 0.1.0
public record DictionaryItemListResponse(
    String typeCode, String labelStandard, List<DictionaryItemResponse> items) {

  /// 带防御性拷贝的规范构造函数。
  public DictionaryItemListResponse {
    items = items != null ? List.copyOf(items) : List.of();
  }
}
