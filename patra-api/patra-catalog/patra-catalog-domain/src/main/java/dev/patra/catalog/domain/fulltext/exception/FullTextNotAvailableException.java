package dev.patra.catalog.domain.fulltext.exception;

import dev.patra.catalog.domain.kernel.exception.CatalogException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 文献的全文不可读、PDF 也不可下载（或要取的图片不属于该篇）。
///
/// @author linqibin
/// @since 0.11.0
public class FullTextNotAvailableException extends CatalogException {

  /// 创建异常。
  ///
  /// @param publicationId 文献 ID
  public FullTextNotAvailableException(Long publicationId) {
    super(
        "Full text not available for publication: " + publicationId, StandardErrorTrait.NOT_FOUND);
  }
}
