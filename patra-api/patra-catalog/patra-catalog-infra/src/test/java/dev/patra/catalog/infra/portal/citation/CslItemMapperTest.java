package dev.patra.catalog.infra.portal.citation;

import static org.assertj.core.api.Assertions.assertThat;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;
import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `CslItemMapper` 单元测试：引用记录 → CSL 条目（spec 第 7.2 节）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CslItemMapper 单元测试")
class CslItemMapperTest {

  @Test
  @DisplayName("全字段：类型、标题、期刊、卷期、页码原文、日期、DOI / PMID / PMCID、id")
  void should_map_full_record() {
    CSLItemData item = CslItemMapper.toCsl(CitationRecordFixture.full());

    assertThat(item.getId()).isEqualTo("1");
    assertThat(item.getType()).isEqualTo(CSLType.ARTICLE_JOURNAL);
    assertThat(item.getTitle())
        .isEqualTo("Empagliflozin in Heart Failure with a Preserved Ejection Fraction");
    assertThat(item.getContainerTitle()).isEqualTo("The New England journal of medicine");
    assertThat(item.getContainerTitleShort()).isEqualTo("N Engl J Med");
    assertThat(item.getVolume()).isEqualTo("385");
    assertThat(item.getIssue()).isEqualTo("16");
    assertThat(item.getPage()).isEqualTo("1451-61");
    assertThat(item.getIssued().getDateParts()).isEqualTo(new int[][] {{2021, 10, 14}});
    assertThat(item.getDOI()).isEqualTo("10.1056/NEJMoa2107038");
    assertThat(item.getPMID()).isEqualTo("34449189");
    assertThat(item.getPMCID()).isEqualTo("PMC8459591");
    assertThat(item.getAuthor()[0].getFamily()).isEqualTo("Anker");
    assertThat(item.getAuthor()[0].getGiven()).isEqualTo("Stefan D");
  }

  @Test
  @DisplayName("只到年：date-parts 为 [[2023]]")
  void should_map_year_only_date_parts() {
    CSLItemData item = CslItemMapper.toCsl(CitationRecordFixture.yearOnlyWithArticleNumber());

    assertThat(item.getIssued().getDateParts()).isEqualTo(new int[][] {{2023}});
  }

  @Test
  @DisplayName("只到年月：date-parts 为 [[2022, 10]]")
  void should_map_year_month_date_parts() {
    CSLItemData item = CslItemMapper.toCsl(CitationRecordFixture.yearMonth());

    assertThat(item.getIssued().getDateParts()).isEqualTo(new int[][] {{2022, 10}});
  }

  @Test
  @DisplayName("没有页码有文章号：page 与 number 都是文章号")
  void should_use_article_number_as_page_and_number_when_no_pagination() {
    CSLItemData item = CslItemMapper.toCsl(CitationRecordFixture.yearOnlyWithArticleNumber());

    assertThat(item.getPage()).isEqualTo("12345");
    assertThat(item.getNumber()).isEqualTo("12345");
  }

  @Test
  @DisplayName("没有 MedlinePgn 原文：page 用首页-尾页")
  void should_use_first_and_last_page_when_no_medline_pgn() {
    CitationRecord record =
        CitationRecord.builder()
            .id(5L)
            .title("Pages only")
            .pagination(Pagination.of(null, "1200", "1210"))
            .build();

    assertThat(CslItemMapper.toCsl(record).getPage()).isEqualTo("1200-1210");
  }

  @Test
  @DisplayName("团体作者映射为 literal")
  void should_map_collective_author_as_literal() {
    CSLName[] authors = CslItemMapper.toCsl(CitationRecordFixture.full()).getAuthor();

    assertThat(authors).hasSize(8);
    assertThat(authors[7].getLiteral()).isEqualTo("EMPEROR-Preserved Trial Investigators");
    assertThat(authors[7].getFamily()).isNull();
  }

  /// PubMed 标题末尾的句点去掉（与 Zotero 导入 PubMed 一致），问号、感叹号保留。
  @Test
  @DisplayName("标题末尾的句点去掉，问号保留")
  void should_strip_trailing_period_from_title() {
    CitationRecord withPeriod =
        CitationRecord.builder()
            .id(6L)
            .title(
                "Promotion - The 4th P of Social Marketing: A Scoping Review of Efforts from"
                    + " 2010-2022.")
            .build();
    CitationRecord withQuestion =
        CitationRecord.builder().id(7L).title("Is aspirin still useful?").build();

    assertThat(CslItemMapper.toCsl(withPeriod).getTitle())
        .isEqualTo(
            "Promotion - The 4th P of Social Marketing: A Scoping Review of Efforts from 2010-2022");
    assertThat(CslItemMapper.toCsl(withQuestion).getTitle()).isEqualTo("Is aspirin still useful?");
  }

  @Test
  @DisplayName("没有作者：不设 author")
  void should_omit_author_when_empty() {
    assertThat(CslItemMapper.toCsl(CitationRecordFixture.withoutAuthors()).getAuthor()).isNull();
  }
}
