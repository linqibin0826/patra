package dev.patra.catalog.app.fulltext.usecase.query;

import dev.patra.catalog.domain.fulltext.exception.FullTextNotAvailableException;
import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.port.read.FullTextFileReadPort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 门户全文文件（PDF、正文图片）的查询。判定与打开分开：控制器先拿到文件信息（可能 404），
/// 处理完条件请求后再打开对象。
///
/// @author linqibin
/// @since 0.11.0
@Service
@RequiredArgsConstructor
public class PortalFullTextFileQueryService {

  private final FullTextFileReadPort readPort;
  private final FullTextObjectStoragePort storage;

  /// 取可下载的 PDF。
  ///
  /// @param id 文献 ID
  /// @return 文件信息
  /// @throws FullTextNotAvailableException 不可下载时抛出
  @Transactional(readOnly = true)
  public FullTextFileRef getPdf(long id) {
    return readPort.findPdf(id).orElseThrow(() -> new FullTextNotAvailableException(id));
  }

  /// 取该篇的正文图片。
  ///
  /// @param id 文献 ID
  /// @param fileName 文件名
  /// @return 文件信息
  /// @throws FullTextNotAvailableException 不可读或图片不属于该篇时抛出
  @Transactional(readOnly = true)
  public FullTextFileRef getFigure(long id, String fileName) {
    return readPort
        .findFigure(id, fileName)
        .orElseThrow(() -> new FullTextNotAvailableException(id));
  }

  /// 打开对象（不在事务里，避免下载期间占用数据库连接）。
  ///
  /// @param ref 文件信息
  /// @return 打开的文件，调用方负责关闭
  public StoredFile open(FullTextFileRef ref) {
    return storage.open(ref.objectKey());
  }
}
