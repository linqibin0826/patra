package dev.patra.catalog.infra.adapter.read;

import dev.patra.catalog.domain.model.enums.PublicationDateType;
import dev.patra.catalog.domain.model.read.citation.CitationAuthor;
import dev.patra.catalog.domain.model.read.citation.CitationRecord;
import dev.patra.catalog.domain.model.vo.publication.ArticleNumber;
import dev.patra.catalog.domain.model.vo.publication.InlineMarkup;
import dev.patra.catalog.domain.model.vo.publication.Pagination;
import dev.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.patra.catalog.domain.port.read.CitationRecordReadPort;
import dev.patra.catalog.infra.persistence.dao.CitationRecordDao;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 引用记录读端适配器：主行与作者两条 SQL，在内存里拼成 [CitationRecord]。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class CitationRecordReadAdapter implements CitationRecordReadPort {

  private final CitationRecordDao dao;

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
    List<CitationRecordRow> rows = dao.findRecords(ids.toArray(Long[]::new));
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<CitationAuthor>> authors =
        dao.findAuthors(rows.stream().map(CitationRecordRow::getId).toArray(Long[]::new)).stream()
            .collect(
                Collectors.groupingBy(
                    CitationAuthorRow::getPublicationId,
                    Collectors.mapping(
                        a ->
                            CitationAuthor.of(
                                a.getFamily(), a.getGiven(), a.getInitials(), a.getCollective()),
                        Collectors.toList())));
    return rows.stream().map(r -> toRecord(r, authors.getOrDefault(r.getId(), List.of()))).toList();
  }

  /// 主行 → 引用记录：标题去标记、缩写兜底、文章号只在没有页码时给、出版日期按精度。
  ///
  /// @param row 主行
  /// @param authors 作者
  /// @return 引用记录
  private static CitationRecord toRecord(CitationRecordRow row, List<CitationAuthor> authors) {
    Pagination pagination =
        Pagination.of(row.getMedlinePgn(), row.getStartPage(), row.getEndPage());
    return CitationRecord.builder()
        .id(row.getId())
        .pmid(row.getPmid())
        .pmcid(row.getPmcid())
        .doi(row.getDoi())
        .title(InlineMarkup.toPlainText(row.getTitle()))
        .authors(authors)
        .journalTitle(row.getJournalTitle())
        .journalAbbreviation(
            firstNonBlank(
                row.getIsoAbbreviation(), row.getMedlineAbbreviation(), row.getJournalTitle()))
        .volume(row.getVolume())
        .issue(row.getIssue())
        .pagination(pagination)
        .articleNumber(ArticleNumber.of(row.getPii(), pagination).orElse(null))
        .publishedDate(
            toDate(row.getPublicationYear(), row.getPublicationMonth(), row.getPublicationDay()))
        .language(row.getLanguage())
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
