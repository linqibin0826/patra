package dev.patra.catalog.domain.fulltext.port.read;

import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import java.util.Optional;

/// 门户全文文件（PDF、正文图片）的读端口。
///
/// @author linqibin
/// @since 0.11.0
public interface FullTextFileReadPort {

  /// PDF：按"可下载"判定（不要求可读全文），ETag 用 PDF 的 md5，文件名为 `{PMCID}.pdf`。
  ///
  /// @param publicationId 文献 ID
  /// @return 文件，不可下载时为空
  Optional<FullTextFileRef> findPdf(long publicationId);

  /// 正文图片：按"可读全文"判定，文件名必须在该篇的 `media` 清单里且对象存在。
  ///
  /// @param publicationId 文献 ID
  /// @param fileName 文件名
  /// @return 文件，不可取时为空
  Optional<FullTextFileRef> findFigure(long publicationId, String fileName);
}
