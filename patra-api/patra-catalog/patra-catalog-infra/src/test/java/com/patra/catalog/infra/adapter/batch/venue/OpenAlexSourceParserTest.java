package com.patra.catalog.infra.adapter.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
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
/// **DDD 嵌入式值对象设计**：
///
/// 聚合根（VenueAggregate）已包含所有嵌入式值对象：
/// - publicationProfile - 出版概况
/// - citationMetrics - 引用指标
/// - openAccess - 开放获取信息
/// - affiliatedSocieties - 关联学会
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
      VenueParseResult result = results.getFirst();
      VenueAggregate venue = result.aggregate();

      // 核心字段在聚合根中
      assertThat(venue.getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S137773608");
      assertThat(venue.getDisplayName()).isEqualTo("Nature");
      assertThat(venue.getIdentifier(VenueIdentifierType.ISSN_L)).hasValue("0028-0836");
      assertThat(venue.getVenueType()).isEqualTo(VenueType.JOURNAL);

      // 嵌入式值对象在聚合根中
      PublicationProfile profile = venue.getPublicationProfile();
      assertThat(profile).isNotNull();
      assertThat(profile.countryCode()).isEqualTo("GB");

      // 开放获取信息在聚合根中
      OpenAccessInfo openAccess = venue.getOpenAccess();
      assertThat(openAccess).isNotNull();
      assertThat(openAccess.isOa()).isFalse();
      assertThat(openAccess.isInDoaj()).isFalse();
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
    @DisplayName("解析完整记录 - 应该正确填充所有嵌入式值对象和年度指标")
    void parseFullRecord_shouldPopulateAllEmbeddedValueObjectsAndMetrics() throws Exception {
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

      // 基础字段（在聚合根中）
      assertThat(venue.getIdentifier(VenueIdentifierType.OPENALEX)).hasValue("S137773608");
      assertThat(venue.getDisplayName()).isEqualTo("Nature");
      assertThat(venue.getIdentifier(VenueIdentifierType.ISSN_L)).hasValue("0028-0836");

      // 出版概况（嵌入式值对象）
      PublicationProfile profile = venue.getPublicationProfile();
      assertThat(profile).isNotNull();
      assertThat(profile.homepageUrl()).isEqualTo("https://www.nature.com/nature/");
      assertThat(profile.abbreviatedTitle()).isEqualTo("Nature");
      assertThat(profile.alternateTitles()).contains("Nature (London)");

      // 宿主机构（在 PublicationProfile 中）
      assertThat(profile.hostOrganization()).isNotNull();
      assertThat(profile.hostOrganization().id()).isEqualTo("P4310319965");
      assertThat(profile.hostOrganization().name()).isEqualTo("Nature Portfolio");

      // 引用指标（嵌入式值对象）
      CitationMetrics metrics = venue.getCitationMetrics();
      assertThat(metrics).isNotNull();
      assertThat(metrics.worksCount()).isEqualTo(500000);
      assertThat(metrics.citedByCount()).isEqualTo(10000000);
      assertThat(metrics.hIndex()).isEqualTo(1200);
      assertThat(metrics.i10Index()).isEqualTo(50000);

      // 开放获取信息（嵌入式值对象，合并 OA 状态 + APC）
      OpenAccessInfo openAccess = venue.getOpenAccess();
      assertThat(openAccess).isNotNull();
      assertThat(openAccess.isOa()).isFalse();
      assertThat(openAccess.isInDoaj()).isFalse();
      assertThat(openAccess.apcUsd()).isEqualTo(11390);
      assertThat(openAccess.apcPrices()).hasSize(1);

      // 关联学会（嵌入式值对象）
      assertThat(venue.getAffiliatedSocieties()).hasSize(1);
      assertThat(venue.getAffiliatedSocieties().getFirst().organization())
          .isEqualTo("Nature Research");

      // 年度指标（从 VenueParseResult 获取，独立存储）
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
