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

  /// 锁住这篇的全文记录并读出当前值（`SELECT … FOR UPDATE`），须在调用方的事务里调用。
  ///
  /// 抓取与解析两条流水线可以同时改同一篇。持久化事务先调它：别的事务正在改这行时等它提交，
  /// 拿到的是提交后的值，据此判断要写的结果还成不成立。
  ///
  /// @param publicationId 文献 ID
  /// @return 当前全文记录；还没有记录时为空（不加锁）
  Optional<PublicationFullText> lockByPublicationId(long publicationId);

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
