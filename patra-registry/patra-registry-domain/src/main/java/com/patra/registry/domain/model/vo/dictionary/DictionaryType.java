package com.patra.registry.domain.model.vo.dictionary;

import com.patra.registry.domain.exception.DomainValidationException;

/// 字典类型领域值对象。
///
/// 表示字典类型的稳定业务键，仅保留解析所需的核心属性。
///
/// @author linqibin
/// @since 0.1.0
public record DictionaryType(Long id, String typeCode) {

  /// 带验证的规范构造函数。
  ///
  /// @param id 字典类型主键
  /// @param typeCode 字典类型代码(小写下划线)
  public DictionaryType {
    DomainValidationException.positive(id, "Dictionary type id");
    typeCode = DomainValidationException.notBlank(typeCode, "Dictionary type code");
  }
}
