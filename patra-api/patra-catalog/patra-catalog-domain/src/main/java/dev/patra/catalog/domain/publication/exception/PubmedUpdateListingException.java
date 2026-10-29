package dev.patra.catalog.domain.publication.exception;

import dev.patra.catalog.domain.kernel.exception.CatalogException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 读取 PubMed updatefiles 目录失败，或目录里找不到任何匹配的文件。
///
/// @author linqibin
/// @since 0.11.0
public class PubmedUpdateListingException extends CatalogException {

  /// 创建异常。
  ///
  /// @param message 错误信息
  public PubmedUpdateListingException(String message) {
    super(message, StandardErrorTrait.DEP_UNAVAILABLE);
  }

  /// 创建带原因的异常。
  ///
  /// @param message 错误信息
  /// @param cause 原因
  public PubmedUpdateListingException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
