package dev.patra.catalog.infra.mesh.persistence.dao;

/// MeSH 主题词建议与按 UI 查询的 native SQL（`@Query` 要求编译期常量）。
///
/// 设计见 `docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md` 第 10.4、10.5 节。
///
/// @author linqibin
/// @since 0.1.0
final class MeshDescriptorSuggestSql {

  /// 候选集上限：泛前缀（`ca`、`pro`）有上千条命中，先按打分截断再按主题词去重。
  static final int CANDIDATE_CAP = 500;

  /// 建议查询：主题词名与非排列入口词都参与，只给主题类（class 1）且 active 的主题词。
  ///
  /// `match_rank`：名称精确 0、入口词精确 1、名称前缀 2、入口词前缀 3、名称词首 4、入口词词首 5。
  /// 两个局部索引的谓词与这里的 WHERE 逐字相同。
  static final String SUGGEST =
      """
      WITH cand AS (
        SELECT d.ui, d.name, CAST(NULL AS varchar) AS matched_term,
               CASE WHEN lower(d.name) = lower(:q) THEN 0
                    WHEN d.name ILIKE :prefix ESCAPE '!' THEN 2 ELSE 4 END AS match_rank
        FROM cat_mesh_descriptor d
        WHERE d.descriptor_class = '1' AND d.active_status
          AND (d.name ILIKE :prefix ESCAPE '!' OR d.name ILIKE :wordStart ESCAPE '!')
        UNION ALL
        SELECT t.owner_ui, d.name, t.term,
               CASE WHEN lower(t.term) = lower(:q) THEN 1
                    WHEN t.term ILIKE :prefix ESCAPE '!' THEN 3 ELSE 5 END
        FROM cat_mesh_entry_term t
        JOIN cat_mesh_descriptor d ON d.ui = t.owner_ui
        WHERE t.record_type = 'DESCRIPTOR' AND t.is_permuted_term = false
          AND d.descriptor_class = '1' AND d.active_status
          AND (t.term ILIKE :prefix ESCAPE '!' OR t.term ILIKE :wordStart ESCAPE '!')),
      capped AS (
        SELECT c.ui, c.name, c.matched_term, c.match_rank
        FROM cand c
        ORDER BY c.match_rank, length(coalesce(c.matched_term, c.name))
        LIMIT """
          + " "
          + CANDIDATE_CAP
          + """
      ),
      best AS (
        SELECT DISTINCT ON (c.ui) c.ui, c.name, c.matched_term, c.match_rank
        FROM capped c
        ORDER BY c.ui, c.match_rank, length(coalesce(c.matched_term, c.name)))
      SELECT b.ui AS "ui", b.name AS "name",
             CASE WHEN b.matched_term IS NULL OR lower(b.matched_term) = lower(b.name)
                  THEN NULL ELSE b.matched_term END AS "matchedTerm",
             (SELECT string_agg(tn.tree_number, ' ' ORDER BY tn.is_primary DESC, tn.tree_number)
              FROM cat_mesh_tree_number tn WHERE tn.descriptor_ui = b.ui) AS "treeNumbers"
      FROM best b
      ORDER BY b.match_rank, length(b.name), b.name
      LIMIT :limit
      """;

  /// 按 UI 查摘要：不限 class 与 active_status（通用的主题词查询）。
  static final String FIND_SUMMARY_BY_UI =
      """
      SELECT d.ui AS "ui", d.name AS "name",
             (SELECT string_agg(tn.tree_number, ' ' ORDER BY tn.is_primary DESC, tn.tree_number)
              FROM cat_mesh_tree_number tn WHERE tn.descriptor_ui = d.ui) AS "treeNumbers"
      FROM cat_mesh_descriptor d
      WHERE d.ui = :ui
      """;

  private MeshDescriptorSuggestSql() {}
}
