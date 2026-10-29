package dev.patra.catalog.infra.portal.citation;

import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.write;
import static dev.patra.catalog.infra.portal.citation.ExportWriterTestSupport.writeSamplesInTwoBatches;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// `CslJsonExportWriter` 单元测试：跨批写成一个 JSON 数组，条目由 `CslItemMapper` 生成。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CslJsonExportWriter 单元测试")
class CslJsonExportWriterTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  @DisplayName("三篇分两批写入：一个数组、三项，id 与 date-parts 按精度")
  void should_write_single_json_array_across_batches() throws IOException {
    JsonNode array = JSON.readTree(writeSamplesInTwoBatches(CslJsonExportWriter::new));

    assertThat(array.isArray()).isTrue();
    assertThat(array.size()).isEqualTo(3);
    assertThat(array.get(0).get("id").asString()).isEqualTo("1");
    assertThat(array.get(0).get("type").asString()).isEqualTo("article-journal");
    assertThat(array.get(0).get("PMID").asString()).isEqualTo("34449189");
    assertThat(array.get(0).get("author").size()).isEqualTo(8);
    assertThat(array.get(0).get("issued").get("date-parts").toString()).isEqualTo("[[2021,10,14]]");
    assertThat(array.get(1).get("issued").get("date-parts").toString()).isEqualTo("[[2023]]");
    assertThat(array.get(2).get("issued").get("date-parts").toString()).isEqualTo("[[2022,10]]");
  }

  @Test
  @DisplayName("没有记录：空数组")
  void should_write_empty_array_when_no_records() throws IOException {
    JsonNode array = JSON.readTree(write(CslJsonExportWriter::new));

    assertThat(array.isArray()).isTrue();
    assertThat(array.size()).isZero();
  }
}
