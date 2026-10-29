package dev.patra.catalog.domain.fulltext.port.repository;

import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import java.util.Optional;

/// 全文记录的持久化。
///
/// @author linqibin
/// @since 0.11.0
public interface PublicationFullTextRepository {

  /// 按文献 ID 查全文记录。
  ///
  /// @param publicationId 文献 ID
  /// @return 全文记录
  Optional<PublicationFullText> findByPublicationId(long publicationId);

  /// 按 `publication_id` upsert：有行则更新全部列，没有则插入。
  ///
  /// @param fullText 全文记录
  void save(PublicationFullText fullText);

  /// 按 `publication_id` upsert 正文。
  ///
  /// @param publicationId 文献 ID
  /// @param body 正文
  /// @param parserVersion 解析器版本
  void saveBody(long publicationId, FullTextBody body, int parserVersion);

  /// 更新 `cat_publication_metadata.has_full_text`；没有 metadata 行时不新建。
  ///
  /// @param publicationId 文献 ID
  /// @param value 是否有全文
  void setHasFullText(long publicationId, boolean value);
}
