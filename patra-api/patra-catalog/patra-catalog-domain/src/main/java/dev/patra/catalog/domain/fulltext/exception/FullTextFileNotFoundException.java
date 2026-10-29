package dev.patra.catalog.domain.fulltext.exception;

import dev.patra.catalog.domain.kernel.exception.CatalogException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 对象存储里找不到全文文件。
///
/// @author linqibin
/// @since 0.11.0
public class FullTextFileNotFoundException extends CatalogException {

  /// 创建异常。
  ///
  /// @param objectKey 对象键
  /// @param cause 原因
  public FullTextFileNotFoundException(String objectKey, Throwable cause) {
    super("全文文件不存在：" + objectKey, cause, StandardErrorTrait.NOT_FOUND);
  }
}
