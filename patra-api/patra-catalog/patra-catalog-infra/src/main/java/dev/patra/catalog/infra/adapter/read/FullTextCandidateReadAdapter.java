package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.enums.FullTextStatus;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.domain.port.read.FullTextCandidateReadPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// 全文候选的 native SQL 读端实现。
///
/// pmc 标识符以 `cat_publication_identifier.type = 'pmc'` 为准；同一篇可能有两条 pmc 标识符
/// （该表没有 `(publication_id, type)` 唯一约束），用 `DISTINCT ON (publication_id)` 去重，
/// 否则同一篇会被两个线程同时处理。
///
/// @author linqibin
/// @since 0.11.0
@Component
public class FullTextCandidateReadAdapter implements FullTextCandidateReadPort {

  private static final String SELECT =
      """
      SELECT DISTINCT ON (i.publication_id)
             i.publication_id, i.identifier_value, f.status, COALESCE(f.attempt_count, 0),
             (SELECT COALESCE(d.date_value, make_date(d.year, COALESCE(d.month, 1), COALESCE(d.day, 1)))
                FROM cat_publication_date d
               WHERE d.publication_id = i.publication_id AND d.date_type = 'pmcrelease'
               ORDER BY d.order_num NULLS LAST LIMIT 1)
        FROM cat_publication_identifier i
        JOIN cat_publication p ON p.id = i.publication_id AND p.deleted_at IS NULL
        LEFT JOIN cat_publication_fulltext f ON f.publication_id = i.publication_id
       WHERE i.type = 'pmc'
      """;

  @PersistenceContext private EntityManager em;

  /// 一批抓取候选。
  ///
  /// @param now 当前时间
  /// @param maxAttempts 抓取失败的重试上限
  /// @param lastPublicationId keyset 游标
  /// @param limit 批量大小
  /// @return 候选
  @Override
  public List<FetchCandidate> findFetchCandidates(
      Instant now, int maxAttempts, long lastPublicationId, int limit) {
    List<?> rows =
        em.createNativeQuery(
                SELECT
                    + """
                     AND i.publication_id > :lastId
                     AND (f.id IS NULL
                          OR (f.status = 'FETCH_FAILED' AND f.next_attempt_at <= :now
                              AND f.attempt_count < :maxAttempts)
                          OR f.pmcid <> i.identifier_value
                          OR (f.status = 'UNAVAILABLE'
                              AND f.unavailable_reason IN ('NOT_IN_BUCKET', 'NOT_OPEN_ACCESS')
                              AND f.next_attempt_at <= :now))
                   ORDER BY i.publication_id, i.id
                   LIMIT :limit
                  """)
            .setParameter("lastId", lastPublicationId)
            .setParameter("now", Timestamp.from(now))
            .setParameter("maxAttempts", maxAttempts)
            .setParameter("limit", limit)
            .getResultList();
    return rows.stream().map(FullTextCandidateReadAdapter::toCandidate).toList();
  }

  /// 手动重抓：按文献 ID 取候选，忽略状态。
  ///
  /// @param publicationIds 文献 ID
  /// @return 候选
  @Override
  public List<FetchCandidate> findByPublicationIds(Collection<Long> publicationIds) {
    if (publicationIds.isEmpty()) {
      return List.of();
    }
    List<?> rows =
        em.createNativeQuery(
                SELECT
                    + """
                     AND i.publication_id IN (:ids)
                   ORDER BY i.publication_id, i.id
                  """)
            .setParameter("ids", publicationIds)
            .getResultList();
    return rows.stream().map(FullTextCandidateReadAdapter::toCandidate).toList();
  }

  /// 结果行转候选。
  private static FetchCandidate toCandidate(Object row) {
    Object[] cols = (Object[]) row;
    return FetchCandidate.builder()
        .publicationId(((Number) cols[0]).longValue())
        .pmcid((String) cols[1])
        .currentStatus(cols[2] == null ? null : FullTextStatus.valueOf((String) cols[2]))
        .attemptCount(((Number) cols[3]).intValue())
        .pmcReleaseDate(toLocalDate(cols[4]))
        .build();
  }

  /// 日期列转 `LocalDate`（驱动可能返回 `java.sql.Date` 或 `LocalDate`）。
  private static LocalDate toLocalDate(Object value) {
    return switch (value) {
      case null -> null;
      case LocalDate date -> date;
      case Date date -> date.toLocalDate();
      default -> LocalDate.parse(value.toString());
    };
  }
}
