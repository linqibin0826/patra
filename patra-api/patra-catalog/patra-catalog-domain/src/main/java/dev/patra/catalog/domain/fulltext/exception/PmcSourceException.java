package dev.patra.catalog.domain.fulltext.exception;

import dev.patra.catalog.domain.kernel.exception.CatalogException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 访问 PMC Cloud Service 失败：HTTP 出错、超时、响应不合法或文件 md5 不符。
///
/// @author linqibin
/// @since 0.11.0
public class PmcSourceException extends CatalogException {

  /// 创建异常。
  ///
  /// @param message 错误信息
  public PmcSourceException(String message) {
    super(message, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建带原因的异常。
  ///
  /// @param message 错误信息
  /// @param cause 原因
  public PmcSourceException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
