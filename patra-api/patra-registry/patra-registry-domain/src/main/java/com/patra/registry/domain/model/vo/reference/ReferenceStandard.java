package com.patra.registry.domain.model.vo.reference;

import com.patra.registry.domain.exception.DomainValidationException;

/// 来源标准领域值对象。
///
/// 表示外部值遵循的格式或规范。
///
/// @author linqibin
/// @since 0.1.0
public record ReferenceStandard(
    Long id, String standardCode, String standardName, boolean enabled) {

  /// 带验证的规范构造函数。
  ///
  /// @param id 主键
  /// @param standardCode 标准代码
  /// @param standardName 标准名称
  /// @param enabled 是否启用
  public ReferenceStandard {
    DomainValidationException.positive(id, "Reference standard id");
    standardCode = DomainValidationException.notBlank(standardCode, "Reference standard code");
    standardName = DomainValidationException.trimOrNull(standardName);
  }
}
