package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.enums.PublicationDateType;
import dev.patra.catalog.domain.model.read.citation.CitationAuthor;
import dev.patra.catalog.domain.model.read.citation.CitationRecord;
import dev.patra.catalog.domain.model.vo.publication.ArticleNumber;
import dev.patra.catalog.domain.model.vo.publication.InlineMarkup;
import dev.patra.catalog.domain.model.vo.publication.Pagination;
import dev.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.patra.catalog.domain.port.read.CitationRecordReadPort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/// 引用记录读端适配器：主行与作者两条 SQL，在内存里拼成 [CitationRecord]。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class CitationRecordReadAdapter implements CitationRecordReadPort {

  /// 按主键批量查主行：文献 + 期刊 + 期次 + PMCID / PII 子查询，排除软删。
  private static final String FIND_RECORDS =
      """
      SELECT p.id AS "id", p.pmid AS "pmid", p.doi AS "doi", p.title AS "title",
             (SELECT i.identifier_value FROM cat_publication_identifier i
               WHERE i.publication_id = p.id AND i.type = 'pmc' ORDER BY i.id ASC LIMIT 1) AS "pmcid",
             (SELECT i.identifier_value FROM cat_publication_identifier i
               WHERE i.publication_id = p.id AND i.type = 'pii' ORDER BY i.id ASC LIMIT 1) AS "pii",
             v.title AS "journalTitle",
             p.journal_iso_abbreviation AS "isoAbbreviation",
             v.abbreviated_title AS "medlineAbbreviation",
             vi.volume AS "volume", vi.issue AS "issue",
             p.medline_pgn AS "medlinePgn", p.start_page AS "startPage", p.end_page AS "endPage",
             p.publication_year AS "publicationYear",
             p.publication_month AS "publicationMonth",
             p.publication_day AS "publicationDay",
             p.language_code AS "language"
      FROM cat_publication p
      LEFT JOIN cat_venue v ON v.id = p.venue_id
      LEFT JOIN cat_venue_instance vi ON vi.id = p.venue_instance_id
      WHERE p.id = ANY((:ids)::bigint[]) AND p.deleted_at IS NULL
      """;

  /// 按文献主键批量查作者，按文献、`author_order` 排序。
  private static final String FIND_AUTHORS =
      """
      SELECT pa.publication_id AS "publicationId", pa.last_name AS "family",
             pa.fore_name AS "given", pa.initials AS "initials",
             pa.collective_name AS "collective"
      FROM cat_publication_author pa
      WHERE pa.publication_id = ANY((:ids)::bigint[])
      ORDER BY pa.publication_id, pa.author_order, pa.id
      """;

  private final JdbcClient jdbc;

  /// 按 ID 批量读取，只返回存在且未软删的文献。
  ///
  /// @param ids 文献 ID，最多 500 个
  /// @return 引用记录，顺序不保证
  /// @throws IllegalArgumentException 超过 500 个时
  @Override
  public List<CitationRecord> findByIds(Collection<Long> ids) {
    if (ids.size() > MAX_BATCH) {
      throw new IllegalArgumentException("一次最多读取 " + MAX_BATCH + " 篇，当前 " + ids.size());
    }
    if (ids.isEmpty()) {
      return List.of();
    }
    List<CitationRecordRow> rows =
        jdbc.sql(FIND_RECORDS)
            .param("ids", ids.toArray(Long[]::new))
            .query(CitationRecordRow.class)
            .list();
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<CitationAuthor>> authors =
        jdbc
            .sql(FIND_AUTHORS)
            .param("ids", rows.stream().map(CitationRecordRow::id).toArray(Long[]::new))
            .query(CitationAuthorRow.class)
            .list()
            .stream()
            .collect(
                Collectors.groupingBy(
                    CitationAuthorRow::publicationId,
                    Collectors.mapping(
                        a -> CitationAuthor.of(a.family(), a.given(), a.initials(), a.collective()),
                        Collectors.toList())));
    return rows.stream().map(r -> toRecord(r, authors.getOrDefault(r.id(), List.of()))).toList();
  }

  /// 主行 → 引用记录：标题去标记、缩写兜底、文章号只在没有页码时给、出版日期按精度。
  ///
  /// @param row 主行
  /// @param authors 作者
  /// @return 引用记录
  private static CitationRecord toRecord(CitationRecordRow row, List<CitationAuthor> authors) {
    Pagination pagination = Pagination.of(row.medlinePgn(), row.startPage(), row.endPage());
    return CitationRecord.builder()
        .id(row.id())
        .pmid(row.pmid())
        .pmcid(row.pmcid())
        .doi(row.doi())
        .title(InlineMarkup.toPlainText(row.title()))
        .authors(authors)
        .journalTitle(row.journalTitle())
        .journalAbbreviation(
            firstNonBlank(row.isoAbbreviation(), row.medlineAbbreviation(), row.journalTitle()))
        .volume(row.volume())
        .issue(row.issue())
        .pagination(pagination)
        .articleNumber(ArticleNumber.of(row.pii(), pagination).orElse(null))
        .publishedDate(toDate(row.publicationYear(), row.publicationMonth(), row.publicationDay()))
        .language(row.language())
        .build();
  }

  /// 由年月日列构造出版日期。
  ///
  /// @param year 年，可空
  /// @param month 月，可空
  /// @param day 日，可空
  /// @return 出版日期；年为空时为 null
  private static PublicationDate toDate(Integer year, Integer month, Integer day) {
    if (year == null) {
      return null;
    }
    if (month == null) {
      return PublicationDate.ofYear(PublicationDateType.PUBLISHED, year);
    }
    if (day == null) {
      return PublicationDate.ofYearMonth(PublicationDateType.PUBLISHED, year, month);
    }
    return PublicationDate.of(PublicationDateType.PUBLISHED, year, month, day);
  }

  /// 第一个非空白的值。
  ///
  /// @param values 候选
  /// @return 第一个非空白的值；都空白时为 null
  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
