package dev.patra.catalog.infra.portal.citation;

import dev.patra.catalog.domain.kernel.model.enums.PublicationDateType;
import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.util.List;

/// 引用与导出测试共用的三篇样例（spec 第 7.4 节）：全字段、只到年 + 文章号、只到年月。
///
/// @author linqibin
/// @since 0.1.0
final class CitationRecordFixture {

  private CitationRecordFixture() {}

  /// 全字段：EMPEROR-Preserved，NEJM 2021-10-14，8 位作者含团体作者。
  ///
  /// @return 引用记录
  static CitationRecord full() {
    return CitationRecord.builder()
        .id(1L)
        .pmid("34449189")
        .pmcid("PMC8459591")
        .doi("10.1056/NEJMoa2107038")
        .title("Empagliflozin in Heart Failure with a Preserved Ejection Fraction")
        .authors(
            List.of(
                CitationAuthor.of("Anker", "Stefan D", "SD", null),
                CitationAuthor.of("Butler", "Javed", "J", null),
                CitationAuthor.of("Filippatos", "Gerasimos", "G", null),
                CitationAuthor.of("Ferreira", "João Pedro", "JP", null),
                CitationAuthor.of("Bocchi", "Edimar", "E", null),
                CitationAuthor.of("Böhm", "Michael", "M", null),
                CitationAuthor.of("Brunner-La Rocca", "Hans-Peter", "HP", null),
                CitationAuthor.of(null, null, null, "EMPEROR-Preserved Trial Investigators")))
        .journalTitle("The New England journal of medicine")
        .journalAbbreviation("N Engl J Med")
        .volume("385")
        .issue("16")
        .pagination(Pagination.of("1451-61", "1451", "1461"))
        .publishedDate(PublicationDate.of(PublicationDateType.PUBLISHED, 2021, 10, 14))
        .language("en")
        .build();
  }

  /// 只到年 + 文章号：Sci Rep 2023，卷 13，没有页码。
  ///
  /// @return 引用记录
  static CitationRecord yearOnlyWithArticleNumber() {
    return CitationRecord.builder()
        .id(2L)
        .doi("10.1038/s41598-023-00001-1")
        .title("A year-only paper")
        .authors(List.of(CitationAuthor.of("Smith", "John A", "JA", null)))
        .journalTitle("Scientific reports")
        .journalAbbreviation("Sci Rep")
        .volume("13")
        .articleNumber("12345")
        .publishedDate(PublicationDate.ofYear(PublicationDateType.PUBLISHED, 2023))
        .language("en")
        .build();
  }

  /// 只到年月：Lancet 2022-10，400(10360):1200-1210。
  ///
  /// @return 引用记录
  static CitationRecord yearMonth() {
    return CitationRecord.builder()
        .id(3L)
        .title("Year month paper")
        .authors(List.of(CitationAuthor.of("Wang", "Li", "L", null)))
        .journalTitle("Lancet")
        .journalAbbreviation("Lancet")
        .volume("400")
        .issue("10360")
        .pagination(Pagination.of("1200-10", "1200", "1210"))
        .publishedDate(PublicationDate.ofYearMonth(PublicationDateType.PUBLISHED, 2022, 10))
        .language("en")
        .build();
  }

  /// 没有作者的文献（PubMed 里有）。
  ///
  /// @return 引用记录
  static CitationRecord withoutAuthors() {
    return CitationRecord.builder()
        .id(4L)
        .pmid("30000004")
        .title("Anonymous editorial")
        .journalTitle("Lancet")
        .journalAbbreviation("Lancet")
        .publishedDate(PublicationDate.ofYear(PublicationDateType.PUBLISHED, 2020))
        .build();
  }

  /// 三篇样例，按 1、2、3 的顺序。
  ///
  /// @return 引用记录
  static List<CitationRecord> samples() {
    return List.of(full(), yearOnlyWithArticleNumber(), yearMonth());
  }
}
