package com.patra.registry.domain.model.vo.reference;

import com.patra.registry.domain.exception.DomainValidationException;

/// 来源标准领域值对象。
///
/// 表示外部值遵循的格式或规范，以及该标准是否为特定字典类型的规范标准。
///
/// 每个字典类型只能有一个规范标准（`canonical = true`），
/// 规范标准定义了 `sys_dict_item.item_code` 应遵循的格式。
///
/// @param id 主键
/// @param dictTypeCode 所属字典类型代码
/// @param standardCode 标准代码
/// @param standardName 标准名称
/// @param canonical 是否为该字典类型的规范标准
/// @param enabled 是否启用
/// @author linqibin
/// @since 0.1.0
public record ReferenceStandard(
    Long id,
    String dictTypeCode,
    String standardCode,
    String standardName,
    boolean canonical,
    boolean enabled) {

  /// 带验证的规范构造函数。
  public ReferenceStandard {
    DomainValidationException.positive(id, "Reference standard id");
    dictTypeCode = DomainValidationException.notBlank(dictTypeCode, "Dict type code");
    standardCode = DomainValidationException.notBlank(standardCode, "Reference standard code");
    standardName = DomainValidationException.trimOrNull(standardName);
  }
}
