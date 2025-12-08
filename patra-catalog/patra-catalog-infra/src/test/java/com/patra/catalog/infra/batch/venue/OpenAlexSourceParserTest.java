package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OpenAlexSourceParser 单元测试。
///
/// **测试策略**：
///
/// - 验证 JSON Lines 解析
/// - 验证 .gz 解压缩
/// - 验证 VenueParseResult 转换（包含聚合根和年度指标）
/// - 验证错误处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OpenAlexSourceParser 单元测试")
class OpenAlexSourceParserTest {

  private OpenAlexSourceParser parser;

  @BeforeEach
  void setUp() {
    parser = new OpenAlexSourceParser();
  }

  @Nested
  @DisplayName("parse() 方法测试")
  class ParseTest {

    @Test
    @DisplayName("解析单条 JSON Lines 记录 - 应该正确转换为 VenueParseResult")
    void parseSingleRecord_shouldConvertToVenueParseResult() throws Exception {
      // Given
      String jsonLine =
          """
          {"id":"https://openalex.org/S137773608","display_name":"Nature","issn_l":"0028-0836","issn":["0028-0836","1476-4687"],"type":"journal","country_code":"GB","is_oa":false,"is_in_doaj":false,"works_count":500000,"cited_by_count":10000000}
          """;
      InputStream input = gzipCompress(jsonLine);

      // When
      List<VenueParseResult> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      VenueAggregate venue = results.getFirst().aggregate();
      assertThat(venue.getOpenalexId()).isEqualTo("S137773608");
      assertThat(venue.getDisplayName()).isEqualTo("Nature");
      assertThat(venue.getIssnL()).isEqualTo("0028-0836");
      assertThat(venue.getVenueType()).isEqualTo(VenueType.JOURNAL);
      assertThat(venue.getCountryCode()).isEqualTo("GB");
      assertThat(venue.isOa()).isFalse();
      assertThat(venue.isInDoaj()).isFalse();
    }

    @Test
    @DisplayName("解析多条 JSON Lines 记录 - 应该返回多个 VenueParseResult")
    void parseMultipleRecords_shouldReturnMultipleResults() throws Exception {
      // Given
      String jsonLines =
          """
          {"id":"https://openalex.org/S1","display_name":"Journal A","type":"journal"}
          {"id":"https://openalex.org/S2","display_name":"Journal B","type":"repository"}
          {"id":"https://openalex.org/S3","display_name":"Journal C","type":"conference"}
          """;
      InputStream input = gzipCompress(jsonLines);

      // When
      List<VenueParseResult> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(3);
      assertThat(results.get(0).aggregate().getDisplayName()).isEqualTo("Journal A");
      assertThat(results.get(0).aggregate().getVenueType()).isEqualTo(VenueType.JOURNAL);
      assertThat(results.get(1).aggregate().getDisplayName()).isEqualTo("Journal B");
      assertThat(results.get(1).aggregate().getVenueType()).isEqualTo(VenueType.REPOSITORY);
      assertThat(results.get(2).aggregate().getDisplayName()).isEqualTo("Journal C");
      assertThat(results.get(2).aggregate().getVenueType()).isEqualTo(VenueType.CONFERENCE);
    }

    @Test
    @DisplayName("解析含空行的 JSON Lines - 应该跳过空行")
    void parseWithEmptyLines_shouldSkipEmptyLines() throws Exception {
      // Given
      String jsonLines =
          """
          {"id":"https://openalex.org/S1","display_name":"Journal A","type":"journal"}

          {"id":"https://openalex.org/S2","display_name":"Journal B","type":"journal"}
          """;
      InputStream input = gzipCompress(jsonLines);

      // When
      List<VenueParseResult> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("解析完整记录 - 应该正确填充所有字段和年度指标")
    void parseFullRecord_shouldPopulateAllFieldsAndMetrics() throws Exception {
      // Given
      String jsonLine =
          """
          {"id":"https://openalex.org/S137773608","display_name":"Nature","issn_l":"0028-0836","issn":["0028-0836","1476-4687"],"type":"journal","country_code":"GB","is_oa":false,"is_in_doaj":false,"homepage_url":"https://www.nature.com/nature/","works_count":500000,"cited_by_count":10000000,"apc_usd":11390,"host_organization":"https://openalex.org/P4310319965","host_organization_name":"Nature Portfolio","summary_stats":{"2yr_mean_citedness":30.5,"h_index":1200,"i10_index":50000},"counts_by_year":[{"year":2024,"works_count":5000,"cited_by_count":100000},{"year":2023,"works_count":4800,"cited_by_count":200000}],"apc_prices":[{"price":11390,"currency":"USD"}],"societies":[{"url":"https://www.nature.com","organization":"Nature Research"}],"abbreviated_title":"Nature","alternate_titles":["Nature (London)"]}
          """;
      InputStream input = gzipCompress(jsonLine);

      // When
      List<VenueParseResult> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      VenueParseResult result = results.getFirst();
      VenueAggregate venue = result.aggregate();

      // 基础字段
      assertThat(venue.getOpenalexId()).isEqualTo("S137773608");
      assertThat(venue.getDisplayName()).isEqualTo("Nature");
      assertThat(venue.getIssnL()).isEqualTo("0028-0836");
      assertThat(venue.getHomepageUrl()).isEqualTo("https://www.nature.com/nature/");
      assertThat(venue.getAbbreviatedTitle()).isEqualTo("Nature");
      assertThat(venue.getAlternateTitles()).contains("Nature (London)");

      // 宿主机构
      assertThat(venue.getHostOrganization()).isNotNull();
      assertThat(venue.getHostOrganization().id()).isEqualTo("P4310319965");
      assertThat(venue.getHostOrganization().name()).isEqualTo("Nature Portfolio");

      // 统计快照
      assertThat(venue.getCurrentStats()).isNotNull();
      assertThat(venue.getCurrentStats().worksCount()).isEqualTo(500000);
      assertThat(venue.getCurrentStats().citedByCount()).isEqualTo(10000000);
      assertThat(venue.getCurrentStats().hIndex()).isEqualTo(1200);
      assertThat(venue.getCurrentStats().i10Index()).isEqualTo(50000);

      // APC 信息
      assertThat(venue.getApcInfo()).isNotNull();
      assertThat(venue.getApcInfo().usd()).isEqualTo(11390);
      assertThat(venue.getApcInfo().prices()).hasSize(1);

      // 学会列表
      assertThat(venue.getSocieties()).hasSize(1);
      assertThat(venue.getSocieties().getFirst().organization()).isEqualTo("Nature Research");

      // 年度指标（从 VenueParseResult 获取，而非聚合根）
      assertThat(result.hasYearlyMetrics()).isTrue();
      assertThat(result.yearlyMetrics()).hasSize(2);
      assertThat(result.yearlyMetrics().get(0).year()).isEqualTo(2024);
      assertThat(result.yearlyMetrics().get(0).worksCount()).isEqualTo(5000);
      assertThat(result.yearlyMetrics().get(1).year()).isEqualTo(2023);
    }
  }

  @Nested
  @DisplayName("toParseResult() 方法测试")
  class ToParseResultTest {

    @Test
    @DisplayName("转换记录时无 type 字段 - 应该使用 OTHER 类型")
    void convertWithoutType_shouldUseOtherType() {
      // Given
      OpenAlexSourceRecord record =
          new OpenAlexSourceRecord(
              "https://openalex.org/S1",
              "Test",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      // When
      VenueParseResult result = parser.toParseResult(record);

      // Then: type 为 null 时应该使用 OTHER 类型（容错处理）
      assertThat(result.aggregate().getVenueType()).isEqualTo(VenueType.OTHER);
    }
  }

  /// 将字符串压缩为 GZIP 格式的 InputStream。
  private InputStream gzipCompress(String content) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
      gzos.write(content.getBytes(StandardCharsets.UTF_8));
    }
    return new ByteArrayInputStream(baos.toByteArray());
  }
}
