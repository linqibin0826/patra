package dev.patra.catalog.infra.fulltext.persistence;

import dev.patra.catalog.domain.fulltext.model.read.FullTextReadModel;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.port.read.PublicationFullTextReadPort;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.catalog.domain.kernel.model.read.FullTextAvailability;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/// 门户全文的 native SQL 读端实现。可读判定见 `FullTextAvailability`；不可读时不查正文表。
///
/// @author linqibin
/// @since 0.11.0
@Component
public class PublicationFullTextReadAdapter implements PublicationFullTextReadPort {

  @PersistenceContext private EntityManager em;

  private final JsonMapper jsonMapper;

  /// 创建读适配器。
  ///
  /// @param jsonMapper 正文 JSON 读取器
  public PublicationFullTextReadAdapter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /// 查门户全文。
  ///
  /// @param publicationId 文献 ID
  /// @return 读模型
  @Override
  public Optional<FullTextReadModel> findForPortal(long publicationId) {
    List<?> rows =
        em.createNativeQuery(
                """
                SELECT f.status, f.pdf_object_key IS NOT NULL, f.pmcid, f.license_code, f.fetched_at,
                       f.word_count, COALESCE(f.is_manuscript, false),
                       EXISTS (SELECT 1 FROM cat_publication_type rt
                                WHERE rt.publication_id = p.id AND rt.type_id = :retractedType)
                  FROM cat_publication p
                  JOIN cat_publication_fulltext f ON f.publication_id = p.id
                 WHERE p.id = :id AND p.deleted_at IS NULL
                """)
            .setParameter("id", publicationId)
            .setParameter("retractedType", FullTextAvailability.RETRACTED_PUBLICATION_TYPE_ID)
            .getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Object[] row = (Object[]) rows.getFirst();
    FullTextAvailability availability =
        FullTextAvailability.of(
            FullTextStatus.valueOf((String) row[0]), (Boolean) row[1], (Boolean) row[7]);
    if (!availability.readable() && !availability.pdfDownloadable()) {
      return Optional.empty();
    }
    return Optional.of(
        FullTextReadModel.builder()
            .publicationId(publicationId)
            .pmcid((String) row[2])
            .licenseCode((String) row[3])
            .fetchedAt(toInstant(row[4]))
            .wordCount(
                availability.readable() && row[5] != null ? ((Number) row[5]).intValue() : null)
            .manuscript((Boolean) row[6])
            .body(availability.readable() ? readBody(publicationId).orElse(null) : null)
            .build());
  }

  /// 读正文 JSON。
  private Optional<FullTextBody> readBody(long publicationId) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT body::text FROM cat_publication_fulltext_body WHERE publication_id = :id")
            .setParameter("id", publicationId)
            .getResultList();
    return rows.isEmpty()
        ? Optional.empty()
        : Optional.of(jsonMapper.readValue((String) rows.getFirst(), FullTextBody.class));
  }

  /// 时间列转 `Instant`（驱动可能返回 `Timestamp`、`Instant` 或 `OffsetDateTime`）。
  private static Instant toInstant(Object value) {
    return switch (value) {
      case null -> null;
      case Instant instant -> instant;
      case Timestamp ts -> ts.toInstant();
      case OffsetDateTime odt -> odt.toInstant();
      default -> Instant.parse(value.toString());
    };
  }
}
