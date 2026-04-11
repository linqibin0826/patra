package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.read.VenueEnrichmentReadPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;

/// [VenueEnrichmentReadPort] 的 JPA 实现，使用 keyset pagination + NOT EXISTS。
///
/// **为什么不用 Repository**：
///
/// 本查询是"工作队列读取"而非聚合根加载，不需要拉起完整 VenueEntity。
/// 使用 JPQL 投影直接构造 [VenueSnapshot]，省掉一次对象图装配。
///
/// **NOT EXISTS 的动态收敛特性**：
///
/// 查询过滤 `NOT EXISTS (SELECT 1 FROM JcrRatingEntity WHERE venue_id = v.id AND year = :targetYear)`，
/// 随 Worker 写入数据，下一批次的候选集合自动缩减。配合 `id > :lastId`
/// keyset 游标，确保当次 run 内失败的 venue 不会被无限重捞。
///
/// @author linqibin
/// @since 0.1.0
@Component
public class VenueEnrichmentReadAdapter implements VenueEnrichmentReadPort {

  @PersistenceContext private EntityManager em;

  /// 查询缺少指定年份 JCR 评级的期刊 venue。
  ///
  /// 使用 keyset pagination（`id > :lastId`）结合 `NOT EXISTS` 子查询过滤已有 JCR 评级的 venue。
  /// 仅返回 `venueType = 'JOURNAL'` 且 `issnL` 不为 null 的 venue。同一次 projection 把
  /// `imageObjectKey` 一起带出，让下游 LetPub Worker 免掉一次 PK 查询。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_jcr_rating.year`），不可为 null
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50），必须 > 0
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  @Override
  public List<VenueSnapshot> findNeedingLetPubEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit) {
    return em.createQuery(
            """
            SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(
                v.id, v.issnL, v.imageObjectKey)
            FROM VenueEntity v
            WHERE v.venueType = 'JOURNAL'
              AND v.issnL IS NOT NULL
              AND v.id > :lastId
              AND NOT EXISTS (
                SELECT 1 FROM JcrRatingEntity j
                WHERE j.venueId = v.id AND j.year = :targetYear
              )
              AND (:minCitedByCount = 0 OR v.citedByCount >= :minCitedByCount)
            ORDER BY v.id ASC
            """,
            VenueSnapshot.class)
        .setParameter("targetYear", targetYear)
        .setParameter("minCitedByCount", minCitedByCount)
        .setParameter("lastId", lastId)
        .setMaxResults(limit)
        .getResultList();
  }

  /// 查询缺少指定年份 Scopus 评级的期刊 venue。
  ///
  /// 与 [findNeedingLetPubEnrichment] 结构一致，但 `NOT EXISTS` 子查询
  /// 指向 `cat_venue_scopus_rating` 表，互不干扰。Scopus 管线不使用 `existingCoverKey`
  /// 字段（恒有值或 null 都无影响），projection 保留是为了与 LetPub 查询共用同一
  /// [VenueSnapshot] record 类型。
  ///
  /// @param targetYear 目标年份（对应 `cat_venue_scopus_rating.year`），不可为 null
  /// @param minCitedByCount 被引次数下限（0 = 不过滤）
  /// @param lastId keyset 游标，传 0 表示从头开始
  /// @param limit 批大小（通常 50），必须 > 0
  /// @return 按 `id` 升序排列的 venue 快照列表；空列表表示没有更多候选
  @Override
  public List<VenueSnapshot> findNeedingScopusEnrichment(
      short targetYear, int minCitedByCount, long lastId, int limit) {
    return em.createQuery(
            """
            SELECT new com.patra.catalog.domain.port.enrichment.VenueSnapshot(
                v.id, v.issnL, v.imageObjectKey)
            FROM VenueEntity v
            WHERE v.venueType = 'JOURNAL'
              AND v.issnL IS NOT NULL
              AND v.id > :lastId
              AND NOT EXISTS (
                SELECT 1 FROM ScopusRatingEntity s
                WHERE s.venueId = v.id AND s.year = :targetYear
              )
              AND (:minCitedByCount = 0 OR v.citedByCount >= :minCitedByCount)
            ORDER BY v.id ASC
            """,
            VenueSnapshot.class)
        .setParameter("targetYear", targetYear)
        .setParameter("minCitedByCount", minCitedByCount)
        .setParameter("lastId", lastId)
        .setMaxResults(limit)
        .getResultList();
  }
}
