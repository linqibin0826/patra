package dev.patra.catalog.infra.publication.persistence;

import dev.patra.catalog.domain.publication.model.enums.SubjectNeighborReason;
import dev.patra.catalog.domain.publication.model.read.SubjectNeighborReadModel;
import dev.patra.catalog.domain.publication.port.read.PublicationNeighborReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/// 最新收录与同主题邻居 CQRS 读适配器：原生 SQL，靠 V14 的两个索引按主键倒序直接取前几行。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PublicationNeighborReadAdapter implements PublicationNeighborReadPort {

  /// 撤稿判定（同详情页）：发表类型 `D016441`，或全文不可用原因 `RETRACTED`；`{pub}` 为文献主键列。
  private static final String RETRACTED =
      """
      (EXISTS (SELECT 1 FROM cat_publication_type rt
                WHERE rt.publication_id = {pub} AND rt.type_id = 'D016441')
       OR EXISTS (SELECT 1 FROM cat_publication_fulltext rf
                   WHERE rf.publication_id = {pub} AND rf.unavailable_reason = 'RETRACTED'))
      """;

  /// 主要主题判定（PubMed `[majr]`）：主题词本身或它的任一副主题词标了 major；`{h}` 为主题词行别名。
  private static final String MAJOR =
      """
      ({h}.is_major_topic OR EXISTS (SELECT 1 FROM cat_publication_mesh_qualifier mq
                                       WHERE mq.publication_mesh_heading_id = {h}.id
                                         AND mq.is_major_topic))
      """;

  /// 最新收录：主键倒序，去掉软删与撤稿。
  private static final String LATEST =
      "SELECT p.id FROM cat_publication p WHERE p.deleted_at IS NULL AND NOT "
          + retracted("p.id")
          + " ORDER BY p.id DESC LIMIT :limit";

  /// 同主题邻居：每个主要主题词各取 `:limit` 篇（`rn` 为词内名次），按（名次，词序）轮流；再补同期刊。
  private static final String NEIGHBORS =
      """
      WITH x AS (
          SELECT id, venue_id FROM cat_publication WHERE id = :id AND deleted_at IS NULL),
      majors AS (
          SELECT h.descriptor_ui,
                 row_number() OVER (ORDER BY h.heading_order NULLS LAST, h.descriptor_ui) AS d_rank
          FROM cat_publication_mesh_heading h JOIN x ON h.publication_id = x.id
          WHERE {major_h}),
      mesh AS (
          SELECT c.publication_id AS id, m.descriptor_ui, m.d_rank,
                 row_number() OVER (PARTITION BY m.descriptor_ui ORDER BY c.publication_id DESC) AS rn
          FROM majors m CROSS JOIN LATERAL (
              SELECT h2.publication_id
              FROM cat_publication_mesh_heading h2
              JOIN cat_publication p2 ON p2.id = h2.publication_id AND p2.deleted_at IS NULL
              WHERE h2.descriptor_ui = m.descriptor_ui
                AND h2.publication_id <> (SELECT id FROM x)
                AND {major_h2}
                AND NOT {retracted_h2}
              ORDER BY h2.publication_id DESC
              LIMIT :limit) c),
      mesh_first AS (
          SELECT DISTINCT ON (id) id, descriptor_ui, d_rank, rn FROM mesh ORDER BY id, rn, d_rank),
      venue AS (
          SELECT v.id, row_number() OVER (ORDER BY v.id DESC) AS rn
          FROM x CROSS JOIN LATERAL (
              SELECT p3.id FROM cat_publication p3
              WHERE p3.venue_id = x.venue_id AND p3.deleted_at IS NULL AND p3.id <> x.id
                AND p3.id NOT IN (SELECT id FROM mesh_first)
                AND NOT {retracted_p3}
              ORDER BY p3.id DESC
              LIMIT :limit) v)
      SELECT u.id, u.reason, u.mesh_descriptor_ui, u.mesh_term, u.venue_name
      FROM (
          SELECT mf.id, 'SAME_MESH' AS reason, mf.descriptor_ui AS mesh_descriptor_ui,
                 d.name AS mesh_term, CAST(NULL AS VARCHAR) AS venue_name, 0 AS grp, mf.rn, mf.d_rank
          FROM mesh_first mf JOIN cat_mesh_descriptor d ON d.ui = mf.descriptor_ui
          UNION ALL
          SELECT venue.id, 'SAME_VENUE', NULL, NULL, cv.title, 1, venue.rn, 0
          FROM venue JOIN x ON true JOIN cat_venue cv ON cv.id = x.venue_id) u
      ORDER BY u.grp, u.rn, u.d_rank, u.id
      LIMIT :limit
      """
          .replace("{major_h}", major("h"))
          .replace("{major_h2}", major("h2"))
          .replace("{retracted_h2}", retracted("h2.publication_id"))
          .replace("{retracted_p3}", retracted("p3.id"));

  private final JdbcClient jdbc;

  /// 撤稿判定片段。
  ///
  /// @param publicationIdColumn 文献主键列
  /// @return SQL 片段
  private static String retracted(String publicationIdColumn) {
    return RETRACTED.replace("{pub}", publicationIdColumn);
  }

  /// 主要主题判定片段。
  ///
  /// @param headingAlias 主题词行别名
  /// @return SQL 片段
  private static String major(String headingAlias) {
    return MAJOR.replace("{h}", headingAlias);
  }

  @Override
  public List<Long> findLatestIds(int limit) {
    return jdbc.sql(LATEST).param("limit", limit).query(Long.class).list();
  }

  @Override
  public List<SubjectNeighborReadModel> findSubjectNeighbors(long publicationId, int limit) {
    return jdbc.sql(NEIGHBORS)
        .param("id", publicationId)
        .param("limit", limit)
        .query(
            (rs, rowNum) ->
                SubjectNeighborReadModel.builder()
                    .id(rs.getLong("id"))
                    .reason(SubjectNeighborReason.valueOf(rs.getString("reason")))
                    .meshDescriptorUi(rs.getString("mesh_descriptor_ui"))
                    .meshTerm(rs.getString("mesh_term"))
                    .venueName(rs.getString("venue_name"))
                    .build())
        .list();
  }
}
