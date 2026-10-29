package dev.patra.catalog.domain.fulltext.port.read;

import dev.patra.catalog.domain.fulltext.model.read.FullTextReadModel;
import java.util.Optional;

/// 门户全文读端口。
///
/// @author linqibin
/// @since 0.11.0
public interface PublicationFullTextReadPort {

  /// 查门户全文：可读时带正文，只有 PDF 可下载时不带正文，两者都不满足返回空。
  ///
  /// @param publicationId 文献 ID
  /// @return 读模型
  Optional<FullTextReadModel> findForPortal(long publicationId);
}
