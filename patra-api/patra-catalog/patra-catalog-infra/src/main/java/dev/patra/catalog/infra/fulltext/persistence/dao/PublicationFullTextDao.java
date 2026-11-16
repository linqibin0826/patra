package dev.patra.catalog.infra.fulltext.persistence.dao;

import dev.patra.catalog.infra.fulltext.persistence.entity.PublicationFullTextEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `cat_publication_fulltext` 数据访问。
///
/// @author linqibin
/// @since 0.11.0
public interface PublicationFullTextDao extends JpaRepository<PublicationFullTextEntity, Long> {

  /// 按文献 ID 查全文记录。
  ///
  /// @param publicationId 文献 ID
  /// @return 实体
  Optional<PublicationFullTextEntity> findByPublicationId(Long publicationId);

  /// 按文献写一份正文，已有则覆盖；`id` 只在新插入时用上。正文表没有实体，只经这条语句写入。
  ///
  /// @param id 新行的雪花 ID
  /// @param publicationId 文献 ID
  /// @param body 正文 JSON
  /// @param version 解析器版本
  /// @return 写入的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO cat_publication_fulltext_body (id, publication_id, body, parser_version)
          VALUES (:id, :publicationId, CAST(:body AS jsonb), :version)
          ON CONFLICT (publication_id) DO UPDATE
             SET body = EXCLUDED.body, parser_version = EXCLUDED.parser_version
          """,
      nativeQuery = true)
  int upsertBody(
      @Param("id") long id,
      @Param("publicationId") long publicationId,
      @Param("body") String body,
      @Param("version") int version);

  /// 更新文献 metadata 的 `has_full_text`；没有 metadata 行时什么也不改。
  ///
  /// metadata 归文献子域，这里按实体名写 JPQL，不依赖它的类。
  ///
  /// @param publicationId 文献 ID
  /// @param value 是否有全文
  /// @return 更新的行数
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE PublicationMetadataEntity m SET m.hasFullText = :value"
          + " WHERE m.publicationId = :publicationId")
  int updateHasFullText(@Param("publicationId") long publicationId, @Param("value") boolean value);
}
