package dev.patra.catalog.infra.portal.citation;

import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.fixture;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.text;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.write;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.writeSamplesInTwoBatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `CsvExportWriter` 单元测试（spec 第 6.4 节 CSV，RFC 4180）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CsvExportWriter 单元测试")
class CsvExportWriterTest {

  @Test
  @DisplayName("BOM 与表头只写一次（跨批），与夹具逐字节相等")
  void should_start_with_bom_and_header_once_across_batches() throws IOException {
    byte[] bytes = writeSamplesInTwoBatches(CsvExportWriter::new);

    assertThat(bytes).isEqualTo(fixture("sample.csv"));
    assertThat(text(bytes).split("id,pmid,", -1)).hasSize(2);
  }

  @Test
  @DisplayName("含逗号、引号、换行的字段加引号，引号翻倍")
  void should_quote_fields_with_comma_quote_newline() throws IOException {
    CitationRecord record = CitationRecord.builder().id(8L).title("A, \"quoted\"\ntitle").build();

    String csv = text(write(CsvExportWriter::new, List.of(record)));

    assertThat(csv).contains("8,,,,\"A, \"\"quoted\"\"\ntitle\",,,,,,,,,\r\n");
  }

  @Test
  @DisplayName("没有记录：只有 BOM 与表头")
  void should_write_header_only_when_no_records() throws IOException {
    String csv = text(write(CsvExportWriter::new));

    assertThat(csv)
        .isEqualTo(
            "﻿id,pmid,pmcid,doi,title,authors,journal,journal_abbreviation,published_date,"
                + "year,volume,issue,pages,article_number\r\n");
  }
}
