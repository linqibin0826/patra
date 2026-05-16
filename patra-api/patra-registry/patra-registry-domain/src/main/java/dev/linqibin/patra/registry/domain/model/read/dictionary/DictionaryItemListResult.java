package dev.linqibin.patra.registry.domain.model.read.dictionary;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.util.List;

/// 字典项列表查询结果。
///
/// 封装指定类型下所有启用字典项及可选的本地化标签。
///
/// @param typeCode 字典类型代码
/// @param labelStandard 本地化标签标准代码，未指定时为 null
/// @param items 字典项摘要列表
/// @author linqibin
/// @since 0.1.0
public record DictionaryItemListResult(
    String typeCode, String labelStandard, List<DictionaryItemSummary> items) {

  /// 带验证的规范构造函数。
  public DictionaryItemListResult {
    typeCode = DomainValidationException.notBlank(typeCode, "字典类型代码");
    labelStandard = DomainValidationException.trimOrNull(labelStandard);
    items = items != null ? List.copyOf(items) : List.of();
  }
}
