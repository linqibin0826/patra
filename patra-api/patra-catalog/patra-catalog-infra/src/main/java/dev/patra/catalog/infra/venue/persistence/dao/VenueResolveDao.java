package dev.patra.catalog.infra.venue.persistence.dao;

import dev.patra.catalog.infra.venue.persistence.VenueNlmMatchRow;
import dev.patra.catalog.infra.venue.persistence.VenueResolveRow;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 期刊批量解析 native query DAO：不过滤软删，只认期刊类型；按主键或 `uk_venue_identifier_nlm` 命中。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueResolveDao extends JpaRepository<VenueEntity, Long> {

  /// 投影列，两种查法共用（以换行结束，后面直接拼 FROM）。
  String COLUMNS =
      """
      v.id AS "id",
             v.nlm_id AS "nlmId",
             v.title AS "title",
             v.abbreviated_title AS "abbreviatedTitle",
             (v.deleted_at IS NOT NULL) AS "deleted"
      """;

  /// 按主键批量查，含软删。
  ///
  /// @param ids 期刊主键数组
  /// @return 投影行
  @Query(
      value =
          "SELECT "
              + COLUMNS
              + """
              FROM cat_venue v
              WHERE v.id = ANY((:ids)::bigint[])
                AND v.venue_type = 'JOURNAL'
              """,
      nativeQuery = true)
  List<VenueResolveRow> findResolveRowsByIds(@Param("ids") Long[] ids);

  /// 按 NLM 批量查，含软删：从标识符表出发，主 NLM、副 NLM 都命中（主 NLM 一定在标识符表里）。
  ///
  /// @param nlmIds NLM ID 数组
  /// @return 投影行，带命中的 NLM
  @Query(
      value =
          "SELECT i.identifier_value AS \"matchedNlmId\", "
              + COLUMNS
              + """
              FROM cat_venue_identifier i
              JOIN cat_venue v ON v.id = i.venue_id
              WHERE i.identifier_type = 'NLM'
                AND i.identifier_value = ANY((:nlmIds)::text[])
                AND v.venue_type = 'JOURNAL'
              """,
      nativeQuery = true)
  List<VenueNlmMatchRow> findResolveRowsByNlmIds(@Param("nlmIds") String[] nlmIds);
}
