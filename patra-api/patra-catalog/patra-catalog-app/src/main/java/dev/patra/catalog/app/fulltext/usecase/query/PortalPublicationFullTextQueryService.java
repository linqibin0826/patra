package dev.patra.catalog.app.fulltext.usecase.query;

import dev.patra.catalog.domain.fulltext.exception.FullTextNotAvailableException;
import dev.patra.catalog.domain.fulltext.model.read.FullTextReadModel;
import dev.patra.catalog.domain.fulltext.port.read.PublicationFullTextReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 门户全文查询。
///
/// @author linqibin
/// @since 0.11.0
@Service
@RequiredArgsConstructor
public class PortalPublicationFullTextQueryService {

  private final PublicationFullTextReadPort readPort;

  /// 查门户全文。
  ///
  /// @param id 文献 ID
  /// @return 读模型
  /// @throws FullTextNotAvailableException 不可读也不能下载 PDF 时抛出
  @Transactional(readOnly = true)
  public FullTextReadModel getForPortal(long id) {
    return readPort.findForPortal(id).orElseThrow(() -> new FullTextNotAvailableException(id));
  }
}
