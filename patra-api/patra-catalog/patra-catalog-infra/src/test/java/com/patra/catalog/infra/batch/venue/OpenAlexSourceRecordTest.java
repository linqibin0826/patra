package com.patra.catalog.infra.batch.venue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/// OpenAlexSourceRecord 单元测试。
///
/// **测试策略**：
///
/// - 验证 JSON 反序列化（snake_case → camelCase）
/// - 验证嵌套对象解析
/// - 验证 OpenAlex ID 提取
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OpenAlexSourceRecord 单元测试")
class OpenAlexSourceRecordTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper =
        JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
  }

  @Nested
  @DisplayName("JSON 反序列化测试")
  class JsonDeserializationTest {

    @Test
    @DisplayName("完整 JSON - 应该正确解析所有字段")
    void fullJson_shouldParseAllFields() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature",
            "issn_l": "0028-0836",
            "issn": ["0028-0836", "1476-4687"],
            "type": "journal",
            "type_id": "https://openalex.org/source-types/journal",
            "country_code": "GB",
            "is_oa": false,
            "is_in_doaj": false,
            "homepage_url": "https://www.nature.com/nature/",
            "works_count": 500000,
            "cited_by_count": 10000000,
            "works_api_url": "https://api.openalex.org/works?filter=primary_location.source.id:S137773608",
            "apc_usd": 11390,
            "host_organization": "https://openalex.org/P4310319965",
            "host_organization_name": "Nature Portfolio",
            "summary_stats": {
              "2yr_mean_citedness": 30.5,
              "h_index": 1200,
              "i10_index": 50000
            },
            "ids": {
              "openalex": "https://openalex.org/S137773608",
              "issn_l": "0028-0836",
              "issn": ["0028-0836", "1476-4687"],
              "wikidata": "https://www.wikidata.org/entity/Q180445"
            },
            "counts_by_year": [
              {"year": 2024, "works_count": 5000, "cited_by_count": 100000},
              {"year": 2023, "works_count": 4800, "cited_by_count": 200000}
            ],
            "apc_prices": [
              {"price": 11390, "currency": "USD"},
              {"price": 9500, "currency": "EUR"}
            ],
            "societies": [
              {"url": "https://www.nature.com", "organization": "Nature Research"}
            ],
            "abbreviated_title": "Nature",
            "alternate_titles": ["Nature (London)", "Nature (Lond.)"],
            "created_date": "2023-01-01",
            "updated_date": "2025-11-02"
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.id()).isEqualTo("https://openalex.org/S137773608");
      assertThat(record.displayName()).isEqualTo("Nature");
      assertThat(record.issnL()).isEqualTo("0028-0836");
      assertThat(record.issn()).containsExactly("0028-0836", "1476-4687");
      assertThat(record.type()).isEqualTo("journal");
      assertThat(record.countryCode()).isEqualTo("GB");
      assertThat(record.isOa()).isFalse();
      assertThat(record.isInDoaj()).isFalse();
      assertThat(record.homepageUrl()).isEqualTo("https://www.nature.com/nature/");
      assertThat(record.worksCount()).isEqualTo(500000);
      assertThat(record.citedByCount()).isEqualTo(10000000);
      assertThat(record.apcUsd()).isEqualTo(11390);
      assertThat(record.hostOrganization()).isEqualTo("https://openalex.org/P4310319965");
      assertThat(record.hostOrganizationName()).isEqualTo("Nature Portfolio");
      assertThat(record.abbreviatedTitle()).isEqualTo("Nature");
      assertThat(record.alternateTitles()).containsExactly("Nature (London)", "Nature (Lond.)");
      assertThat(record.createdDate()).isEqualTo("2023-01-01");
      assertThat(record.updatedDate()).isEqualTo("2025-11-02");
    }

    @Test
    @DisplayName("嵌套 summaryStats - 应该正确解析")
    void nestedSummaryStats_shouldParse() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature",
            "summary_stats": {
              "2yr_mean_citedness": 30.5,
              "h_index": 1200,
              "i10_index": 50000
            }
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.summaryStats()).isNotNull();
      assertThat(record.summaryStats().twoYrMeanCitedness()).isEqualTo(30.5);
      assertThat(record.summaryStats().hIndex()).isEqualTo(1200);
      assertThat(record.summaryStats().i10Index()).isEqualTo(50000);
    }

    @Test
    @DisplayName("嵌套 ids - 应该正确解析")
    void nestedIds_shouldParse() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature",
            "ids": {
              "openalex": "https://openalex.org/S137773608",
              "issn_l": "0028-0836",
              "issn": ["0028-0836", "1476-4687"],
              "wikidata": "https://www.wikidata.org/entity/Q180445"
            }
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.ids()).isNotNull();
      assertThat(record.ids().openalex()).isEqualTo("https://openalex.org/S137773608");
      assertThat(record.ids().issnL()).isEqualTo("0028-0836");
      assertThat(record.ids().issn()).containsExactly("0028-0836", "1476-4687");
      assertThat(record.ids().wikidata()).isEqualTo("https://www.wikidata.org/entity/Q180445");
    }

    @Test
    @DisplayName("嵌套 countsByYear - 应该正确解析")
    void nestedCountsByYear_shouldParse() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature",
            "counts_by_year": [
              {"year": 2024, "works_count": 5000, "cited_by_count": 100000},
              {"year": 2023, "works_count": 4800, "cited_by_count": 200000}
            ]
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.countsByYear()).hasSize(2);
      assertThat(record.countsByYear().get(0).year()).isEqualTo(2024);
      assertThat(record.countsByYear().get(0).worksCount()).isEqualTo(5000);
      assertThat(record.countsByYear().get(0).citedByCount()).isEqualTo(100000);
    }

    @Test
    @DisplayName("嵌套 apcPrices - 应该正确解析")
    void nestedApcPrices_shouldParse() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature",
            "apc_prices": [
              {"price": 11390, "currency": "USD"},
              {"price": 9500, "currency": "EUR"}
            ]
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.apcPrices()).hasSize(2);
      assertThat(record.apcPrices().get(0).price()).isEqualTo(11390);
      assertThat(record.apcPrices().get(0).currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("嵌套 societies - 应该正确解析")
    void nestedSocieties_shouldParse() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature",
            "societies": [
              {"url": "https://www.nature.com", "organization": "Nature Research"}
            ]
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.societies()).hasSize(1);
      assertThat(record.societies().get(0).url()).isEqualTo("https://www.nature.com");
      assertThat(record.societies().get(0).organization()).isEqualTo("Nature Research");
    }

    @Test
    @DisplayName("最小 JSON（仅必填字段）- 应该正确解析")
    void minimalJson_shouldParse() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature"
          }
          """;

      // When
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // Then
      assertThat(record.id()).isEqualTo("https://openalex.org/S137773608");
      assertThat(record.displayName()).isEqualTo("Nature");
      assertThat(record.issnL()).isNull();
      assertThat(record.issn()).isNull();
      assertThat(record.summaryStats()).isNull();
      assertThat(record.countsByYear()).isNull();
    }
  }

  @Nested
  @DisplayName("OpenAlex ID 提取测试")
  class ExtractOpenAlexIdTest {

    @Test
    @DisplayName("extractOpenAlexId() - 应该提取 ID 后缀")
    void extractOpenAlexId_shouldExtractIdSuffix() throws Exception {
      // Given
      String json =
          """
          {
            "id": "https://openalex.org/S137773608",
            "display_name": "Nature"
          }
          """;
      OpenAlexSourceRecord record = objectMapper.readValue(json, OpenAlexSourceRecord.class);

      // When
      String openAlexId = record.extractOpenAlexId();

      // Then
      assertThat(openAlexId).isEqualTo("S137773608");
    }

    @Test
    @DisplayName("extractOpenAlexId() null id - 应该返回 null")
    void extractOpenAlexId_nullId_shouldReturnNull() {
      // Given
      OpenAlexSourceRecord record =
          new OpenAlexSourceRecord(
              null, // id
              "Test", // displayName
              null, // issnL
              null, // issn
              null, // type
              null, // typeId
              null, // countryCode
              null, // isOa
              null, // isInDoaj
              null, // homepageUrl
              null, // worksCount
              null, // citedByCount
              null, // worksApiUrl
              null, // apcUsd
              null, // hostOrganization
              null, // hostOrganizationName
              null, // hostOrganizationLineage
              null, // summaryStats
              null, // ids
              null, // countsByYear
              null, // apcPrices
              null, // societies
              null, // abbreviatedTitle
              null, // alternateTitles
              null, // createdDate
              null); // updatedDate

      // When
      String openAlexId = record.extractOpenAlexId();

      // Then
      assertThat(openAlexId).isNull();
    }
  }
}
