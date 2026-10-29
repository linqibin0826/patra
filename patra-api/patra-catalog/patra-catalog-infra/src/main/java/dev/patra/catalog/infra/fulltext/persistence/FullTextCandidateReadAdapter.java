package dev.patra.catalog.infra.fulltext.persistence;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.port.read.FullTextCandidateReadPort;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.catalog.infra.fulltext.persistence.entity.PublicationFullTextEntity;
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
/// （该表没有 `(publication_id, type)` 唯一约束），一律取 id 最小的那条：新文献用 `DISTINCT ON`，
/// 从全文记录出发的查询用 LATERAL 子查询，避免两条不同值时每次都判为 PMCID 变了。
///
/// 抓取候选分三路（新文献、到期、PMCID 变了），各自走合适的索引，而不是一条 OR 查询扫全量。
///
/// @author linqibin
/// @since 0.11.0
@Component
public class FullTextCandidateReadAdapter implements FullTextCandidateReadPort {

  /// PMC 放开日期子查询（取第一条 `pmcrelease` 日期）。
  private static final String RELEASE_DATE =
      """
      (SELECT COALESCE(d.date_value, make_date(d.year, COALESCE(d.month, 1), COALESCE(d.day, 1)))
         FROM cat_publication_date d
        WHERE d.publication_id = {publicationId} AND d.date_type = 'pmcrelease'
        ORDER BY d.order_num NULLS LAST LIMIT 1)
      """;

  /// 从全文记录出发、带当前第一条 pmc 标识符的查询头。
  private static final String FROM_FULLTEXT =
      """
      SELECT f.publication_id, pi.identifier_value, f.status, f.attempt_count, {releaseDate}
        FROM cat_publication_fulltext f
        JOIN cat_publication p ON p.id = f.publication_id AND p.deleted_at IS NULL
        JOIN LATERAL (SELECT i.identifier_value FROM cat_publication_identifier i
                       WHERE i.publication_id = f.publication_id AND i.type = 'pmc'
                       ORDER BY i.id LIMIT 1) pi ON true
      """
          .replace("{releaseDate}", releaseDate("f.publication_id"));

  @PersistenceContext private EntityManager em;

  /// 按文献 ID 列生成放开日期子查询（SQL 文本块含换行，不用 `formatted`）。
  ///
  /// @param publicationIdColumn 文献 ID 列
  /// @return 子查询
  private static String releaseDate(String publicationIdColumn) {
    return RELEASE_DATE.replace("{publicationId}", publicationIdColumn);
  }

  /// 新文献候选。
  ///
  /// @param lastPublicationId keyset 游标
  /// @param limit 批量大小
  /// @return 候选
  @Override
  public List<FetchCandidate> findNewFetchCandidates(long lastPublicationId, int limit) {
    List<?> rows =
        em.createNativeQuery(
                """
                SELECT DISTINCT ON (i.publication_id)
                       i.publication_id, i.identifier_value, NULL, 0, {releaseDate}
                  FROM cat_publication_identifier i
                  JOIN cat_publication p ON p.id = i.publication_id AND p.deleted_at IS NULL
                 WHERE i.type = 'pmc' AND i.publication_id > :lastId
                   AND NOT EXISTS (SELECT 1 FROM cat_publication_fulltext f
                                    WHERE f.publication_id = i.publication_id)
                 ORDER BY i.publication_id, i.id
                 LIMIT :limit
                """
                    .replace("{releaseDate}", releaseDate("i.publication_id")))
            .setParameter("lastId", lastPublicationId)
            .setParameter("limit", limit)
            .getResultList();
    return rows.stream().map(FullTextCandidateReadAdapter::toCandidate).toList();
  }

  /// 到期候选。
  ///
  /// @param now 当前时间
  /// @param maxAttempts 重试上限
  /// @param limit 批量大小
  /// @return 候选
  @Override
  public List<FetchCandidate> findDueFetchCandidates(Instant now, int maxAttempts, int limit) {
    List<?> rows =
        em.createNativeQuery(
                FROM_FULLTEXT
                    + """
                     WHERE f.status IN ('FETCH_FAILED', 'UNAVAILABLE')
                       AND f.next_attempt_at <= :now
                       AND ((f.status = 'FETCH_FAILED' AND f.attempt_count < :maxAttempts)
                            OR (f.status = 'UNAVAILABLE'
                                AND f.unavailable_reason IN ('NOT_IN_BUCKET', 'NOT_OPEN_ACCESS')))
                     ORDER BY f.next_attempt_at, f.publication_id
                     LIMIT :limit
                    """)
            .setParameter("now", Timestamp.from(now))
            .setParameter("maxAttempts", maxAttempts)
            .setParameter("limit", limit)
            .getResultList();
    return rows.stream().map(FullTextCandidateReadAdapter::toCandidate).toList();
  }

  /// PMCID 变了的候选。
  ///
  /// @param now 当前时间
  /// @param lastPublicationId keyset 游标
  /// @param limit 批量大小
  /// @return 候选
  @Override
  public List<FetchCandidate> findPmcidChangedCandidates(
      Instant now, long lastPublicationId, int limit) {
    List<?> rows =
        em.createNativeQuery(
                FROM_FULLTEXT
                    + """
                     WHERE f.publication_id > :lastId
                       AND f.pmcid <> pi.identifier_value
                       AND (f.next_attempt_at IS NULL OR f.next_attempt_at <= :now)
                     ORDER BY f.publication_id
                     LIMIT :limit
                    """)
            .setParameter("lastId", lastPublicationId)
            .setParameter("now", Timestamp.from(now))
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
                """
                SELECT DISTINCT ON (i.publication_id)
                       i.publication_id, i.identifier_value, f.status, COALESCE(f.attempt_count, 0), {releaseDate}
                  FROM cat_publication_identifier i
                  JOIN cat_publication p ON p.id = i.publication_id AND p.deleted_at IS NULL
                  LEFT JOIN cat_publication_fulltext f ON f.publication_id = i.publication_id
                 WHERE i.type = 'pmc' AND i.publication_id IN (:ids)
                 ORDER BY i.publication_id, i.id
                """
                    .replace("{releaseDate}", releaseDate("i.publication_id")))
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

  /// 一批解析候选（JPQL 取实体，`media` 由 JSONB 映射；`@SoftDelete` 自动排除已删除的文献）。
  ///
  /// @param parserVersion 当前解析器版本
  /// @param lastPublicationId keyset 游标
  /// @param limit 批量大小
  /// @return 候选
  @Override
  public List<ParseCandidate> findParseCandidates(
      int parserVersion, long lastPublicationId, int limit) {
    return em
        .createQuery(
            """
            SELECT f FROM PublicationFullTextEntity f
            WHERE f.publicationId > :lastId
              AND EXISTS (SELECT 1 FROM PublicationEntity p WHERE p.id = f.publicationId)
              AND (f.status = :fetched
                   OR (f.status IN (:parsed, :parseFailed) AND f.parserVersion < :version)
                   OR (f.status = :unavailable AND f.unavailableReason = :noBody
                       AND f.parserVersion < :version))
            ORDER BY f.publicationId ASC
            """,
            PublicationFullTextEntity.class)
        .setParameter("lastId", lastPublicationId)
        .setParameter("fetched", FullTextStatus.FETCHED)
        .setParameter("parsed", FullTextStatus.PARSED)
        .setParameter("parseFailed", FullTextStatus.PARSE_FAILED)
        .setParameter("unavailable", FullTextStatus.UNAVAILABLE)
        .setParameter("noBody", FullTextUnavailableReason.NO_BODY)
        .setParameter("version", parserVersion)
        .setMaxResults(limit)
        .getResultList()
        .stream()
        .map(
            f ->
                ParseCandidate.builder()
                    .publicationId(f.getPublicationId())
                    .pmcid(f.getPmcid())
                    .pmcVersion(f.getPmcVersion())
                    .objectPrefix(f.getObjectPrefix())
                    .media(f.getMedia())
                    .build())
        .toList();
  }
}
