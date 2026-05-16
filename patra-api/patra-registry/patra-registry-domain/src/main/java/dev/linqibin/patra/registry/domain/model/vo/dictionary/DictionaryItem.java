package dev.linqibin.patra.registry.domain.model.vo.dictionary;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;

/// 字典项领域值对象。
///
/// 仅包含解析过程所需的最小字段集。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryItem(
    Long id, Long typeId, String itemCode, String itemName, boolean enabled) {

  /// 带验证的规范构造函数。
  ///
  /// @param id 字典项主键
  /// @param typeId 字典类型主键
  /// @param itemCode 字典项代码
  /// @param itemName 字典项名称(可为 null)
  /// @param enabled 是否启用
  public DictionaryItem {
    DomainValidationException.positive(id, "Dictionary item id");
    DomainValidationException.positive(typeId, "Dictionary type id");
    itemCode = DomainValidationException.notBlank(itemCode, "Dictionary item code");
    itemName = DomainValidationException.trimOrNull(itemName);
  }
}
