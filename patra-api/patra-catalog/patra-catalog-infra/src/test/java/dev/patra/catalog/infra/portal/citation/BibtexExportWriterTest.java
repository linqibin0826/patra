package dev.patra.catalog.infra.portal.citation;

import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.fixture;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.text;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.write;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.writeSamplesInTwoBatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `BibtexExportWriter` 单元测试（spec 第 6.4 节 BibTeX）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BibtexExportWriter 单元测试")
class BibtexExportWriterTest {

  @Test
  @DisplayName("三篇样例分两批写入，与夹具逐字节相等")
  void should_match_fixture() throws IOException {
    assertThat(writeSamplesInTwoBatches(BibtexExportWriter::new)).isEqualTo(fixture("sample.bib"));
  }

  @Test
  @DisplayName("只到年：不写 month；到月：写 month 宏")
  void should_omit_month_for_year_precision() throws IOException {
    String yearOnly =
        text(
            write(
                BibtexExportWriter::new,
                List.of(CitationRecordFixture.yearOnlyWithArticleNumber())));
    String yearMonth =
        text(write(BibtexExportWriter::new, List.of(CitationRecordFixture.yearMonth())));

    assertThat(yearOnly).doesNotContain("month");
    assertThat(yearMonth).contains("  month = oct,\n");
  }

  @Test
  @DisplayName("TeX 特殊字符转义，标题双花括号")
  void should_escape_tex_specials_and_brace_title() throws IOException {
    CitationRecord record =
        CitationRecord.builder()
            .id(9L)
            .title("CO2 & H_2O: 50% {gain} at $5 #1 ~x^2 \\ end")
            .authors(List.of(CitationAuthor.of("O_Brien", "A & B", "AB", null)))
            .journalTitle("Journal of R&D")
            .build();

    String bib = text(write(BibtexExportWriter::new, List.of(record)));

    assertThat(bib)
        .contains(
            "  title = {{CO2 \\& H\\_2O: 50\\% \\{gain\\} at \\$5 \\#1"
                + " \\textasciitilde{}x\\textasciicircum{}2 \\textbackslash{} end}},\n")
        .contains("  author = {O\\_Brien, A \\& B},\n")
        .contains("  journal = {Journal of R\\&D},\n");
  }

  @Test
  @DisplayName("页码用双连字符")
  void should_use_double_dash_page_range() throws IOException {
    String bib = text(write(BibtexExportWriter::new, List.of(CitationRecordFixture.full())));

    assertThat(bib).contains("  pages = {1451--1461},\n");
  }

  @Test
  @DisplayName("没有作者：不写 author；没有 PMID：键为 patra<id>")
  void should_skip_author_when_empty() throws IOException {
    CitationRecord record =
        CitationRecord.builder().id(7L).title("Anonymous").journalTitle("Lancet").build();

    String bib = text(write(BibtexExportWriter::new, List.of(record)));

    assertThat(bib).startsWith("@article{patra7,\n").doesNotContain("author");
  }
}
