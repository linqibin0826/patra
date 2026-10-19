package dev.patra.catalog.domain.port.repository;

import dev.patra.catalog.domain.model.vo.fulltext.PublicationFullText;
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
}
