package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// ROR 导入参数非法异常。
///
/// 当 ROR 导入参数缺失或不符合规范时抛出。
///
/// @author linqibin
/// @since 0.1.0
public class InvalidRorImportParamsException extends CatalogException {

  /// 构造非法参数异常。
  ///
  /// @param message 详细错误信息
  public InvalidRorImportParamsException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }
}
