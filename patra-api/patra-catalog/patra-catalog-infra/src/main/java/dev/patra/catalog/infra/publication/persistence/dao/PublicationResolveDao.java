package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.PublicationResolveRow;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献批量解析 native query DAO：不过滤软删，按主键或 `uk_pmid` 命中。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationResolveDao extends JpaRepository<PublicationEntity, Long> {

  /// 投影列与 FROM，两种查法共用。
  String SELECT =
      """
      SELECT p.id AS "id",
             p.pmid AS "pmid",
             p.title AS "title",
             v.title AS "venueName",
             p.publication_year AS "publicationYear",
             p.publication_month AS "publicationMonth",
             p.publication_day AS "publicationDay",
             (p.deleted_at IS NOT NULL) AS "deleted"
      FROM cat_publication p
      LEFT JOIN cat_venue v ON v.id = p.venue_id
      """;

  /// 按主键批量查，含软删。
  ///
  /// @param ids 文献主键数组
  /// @return 投影行
  @Query(value = SELECT + "WHERE p.id = ANY((:ids)::bigint[])", nativeQuery = true)
  List<PublicationResolveRow> findResolveRowsByIds(@Param("ids") Long[] ids);

  /// 按 PMID 批量查，含软删。
  ///
  /// @param pmids PMID 数组
  /// @return 投影行
  @Query(value = SELECT + "WHERE p.pmid = ANY((:pmids)::text[])", nativeQuery = true)
  List<PublicationResolveRow> findResolveRowsByPmids(@Param("pmids") String[] pmids);
}
