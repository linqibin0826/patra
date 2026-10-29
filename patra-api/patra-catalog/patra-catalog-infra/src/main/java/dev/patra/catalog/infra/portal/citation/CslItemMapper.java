package dev.patra.catalog.infra.portal.citation;

import de.undercouch.citeproc.csl.CSLDateBuilder;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLNameBuilder;
import de.undercouch.citeproc.csl.CSLType;
import dev.patra.catalog.domain.kernel.model.vo.PageRange;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.util.Optional;

/// 引用记录 → CSL 条目（spec 第 7.2 节），复制引用与 CSL-JSON 导出共用。
///
/// @author linqibin
/// @since 0.1.0
public final class CslItemMapper {

  private CslItemMapper() {}

  /// 转换一篇。
  ///
  /// @param record 引用记录
  /// @return CSL 条目；`id` 为文献 ID 字符串，没有作者时不设 `author`
  public static CSLItemData toCsl(CitationRecord record) {
    CSLItemDataBuilder builder =
        new CSLItemDataBuilder()
            .id(String.valueOf(record.id()))
            .type(CSLType.ARTICLE_JOURNAL)
            .title(stripTrailingPeriod(record.title()))
            .containerTitle(record.journalTitle())
            .containerTitleShort(record.journalAbbreviation())
            .volume(record.volume())
            .issue(record.issue())
            .page(page(record))
            .number(record.articleNumber())
            .DOI(record.doi())
            .PMID(record.pmid())
            .PMCID(record.pmcid())
            .language(record.language());
    if (!record.authors().isEmpty()) {
      builder.author(record.authors().stream().map(CslItemMapper::toName).toArray(CSLName[]::new));
    }
    if (record.publishedDate() != null) {
      builder.issued(
          new CSLDateBuilder().dateParts(new int[][] {dateParts(record.publishedDate())}).build());
    }
    return builder.build();
  }

  /// 去掉标题末尾的一个句点：PubMed 的 `ArticleTitle` 以句点结尾，CSL 样式会在标题后再接标点或文献类型
  /// （GB/T 的 `[J]`），留着会出现「….[J/OL]」。与 Zotero 导入 PubMed 的做法一致；问号、感叹号保留。
  ///
  /// @param title 标题，可空
  /// @return 去掉末尾句点后的标题；入参为 null 时为 null
  private static String stripTrailingPeriod(String title) {
    if (title == null) {
      return null;
    }
    String trimmed = title.strip();
    return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /// 页码：MedlinePgn 原文 → 首页-尾页 → 文章号。
  ///
  /// @param record 引用记录
  /// @return 页码，可空
  private static String page(CitationRecord record) {
    String medlinePgn = record.pagination().medlinePgn();
    if (medlinePgn != null && !medlinePgn.isBlank()) {
      return medlinePgn.trim();
    }
    Optional<PageRange> range = record.pagination().firstRange();
    if (range.isPresent()) {
      PageRange r = range.get();
      return r.last() == null ? r.first() : r.first() + "-" + r.last();
    }
    return record.articleNumber();
  }

  /// 作者：团体作者用 `literal`，个人作者用 `family` / `given`。
  ///
  /// @param author 作者
  /// @return CSL 姓名
  private static CSLName toName(CitationAuthor author) {
    if (author.collective() != null && !author.collective().isBlank()) {
      return new CSLNameBuilder().literal(author.collective()).build();
    }
    String given = author.given() != null ? author.given() : author.initials();
    return new CSLNameBuilder().family(author.family()).given(given).build();
  }

  /// 按精度给出 date-parts。
  ///
  /// @param date 出版日期
  /// @return `[年]`、`[年, 月]` 或 `[年, 月, 日]`
  private static int[] dateParts(PublicationDate date) {
    if (date.month() == null) {
      return new int[] {date.year()};
    }
    if (date.day() == null) {
      return new int[] {date.year(), date.month()};
    }
    return new int[] {date.year(), date.month(), date.day()};
  }
}
