package dev.patra.catalog.infra.portal.citation;

import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.fixture;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.text;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.write;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.writeSamplesInTwoBatches;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `RisExportWriter` 单元测试（spec 第 6.4 节 RIS）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RisExportWriter 单元测试")
class RisExportWriterTest {

  @Test
  @DisplayName("三篇样例分两批写入，与夹具逐字节相等（CRLF）")
  void should_write_full_record_with_crlf() throws IOException {
    byte[] bytes = writeSamplesInTwoBatches(RisExportWriter::new);

    assertThat(bytes).isEqualTo(fixture("sample.ris"));
    assertThat(text(bytes).replace("\r\n", "")).doesNotContain("\n");
  }

  @Test
  @DisplayName("DA 按精度：2021/10/14/、2022/10//、2023///")
  void should_write_da_by_precision() throws IOException {
    String ris = text(writeSamplesInTwoBatches(RisExportWriter::new));

    assertThat(ris).contains("DA  - 2021/10/14/\r\n", "DA  - 2022/10//\r\n", "DA  - 2023///\r\n");
  }

  @Test
  @DisplayName("没有页码有文章号：SP 与 C7 都是文章号，没有 EP")
  void should_write_article_number_as_sp_and_c7() throws IOException {
    String ris =
        text(
            write(
                RisExportWriter::new, List.of(CitationRecordFixture.yearOnlyWithArticleNumber())));

    assertThat(ris).contains("SP  - 12345\r\n", "C7  - 12345\r\n").doesNotContain("EP  - ");
  }

  @Test
  @DisplayName("PMID 写成 AN、M2「PMID: …」与 PubMed 链接")
  void should_write_pmid_as_an_and_m2_and_pubmed_url() throws IOException {
    String ris = text(write(RisExportWriter::new, List.of(CitationRecordFixture.full())));

    assertThat(ris)
        .contains(
            "AN  - 34449189\r\n",
            "M2  - PMID: 34449189\r\n",
            "UR  - https://pubmed.ncbi.nlm.nih.gov/34449189/\r\n");
  }

  @Test
  @DisplayName("没有作者：不写 AU")
  void should_skip_au_when_no_authors() throws IOException {
    String ris = text(write(RisExportWriter::new, List.of(CitationRecordFixture.withoutAuthors())));

    assertThat(ris).startsWith("TY  - JOUR\r\n").doesNotContain("AU  - ");
  }
}
