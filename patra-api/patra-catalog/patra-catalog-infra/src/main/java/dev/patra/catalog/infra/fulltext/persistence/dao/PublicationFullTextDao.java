package dev.patra.catalog.infra.fulltext.persistence.dao;

import dev.patra.catalog.infra.fulltext.persistence.entity.PublicationFullTextEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
