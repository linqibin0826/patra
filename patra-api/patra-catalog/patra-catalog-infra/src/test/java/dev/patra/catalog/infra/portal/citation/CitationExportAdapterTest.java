package dev.patra.catalog.infra.portal.citation;

import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `CitationExportAdapter` 单元测试：逐批拉取、文件头尾各写一次、空批不影响输出。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CitationExportAdapter 单元测试")
class CitationExportAdapterTest {

  private final CitationExportAdapter adapter = new CitationExportAdapter();

  @Test
  @DisplayName("空批与有记录的批混合：只写有记录的批，表头一次")
  void should_write_header_once_and_skip_empty_batches() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<List<CitationRecord>> batches =
        List.of(List.of(), List.of(CitationRecordFixture.full()), List.of());

    adapter.export(ExportFormat.CSV, batches.iterator(), out);

    String csv = text(out.toByteArray());
    assertThat(csv.split("\r\n")).hasSize(2);
    assertThat(csv).startsWith("﻿id,pmid,").contains("\r\n1,34449189,");
  }

  @Test
  @DisplayName("一批都没有：仍是合法文件（CSL-JSON 空数组）")
  void should_write_valid_empty_file_when_no_batches() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    adapter.export(ExportFormat.CSL_JSON, Collections.emptyIterator(), out);

    assertThat(text(out.toByteArray())).isEqualTo("[]\n");
  }

  @Test
  @DisplayName("每批写完即刷出：拉下一批之前，上一批的字节已经到了输出流")
  void should_flush_each_batch_before_pulling_the_next() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<Integer> sizesWhenPulled = new ArrayList<>();
    Iterator<List<CitationRecord>> batches =
        new Iterator<>() {
          private int pulled;

          @Override
          public boolean hasNext() {
            return pulled < 2;
          }

          @Override
          public List<CitationRecord> next() {
            sizesWhenPulled.add(out.size());
            pulled++;
            return List.of(
                pulled == 1 ? CitationRecordFixture.full() : CitationRecordFixture.yearMonth());
          }
        };

    adapter.export(ExportFormat.RIS, batches, out);

    assertThat(sizesWhenPulled.get(1)).isPositive();
  }
}
