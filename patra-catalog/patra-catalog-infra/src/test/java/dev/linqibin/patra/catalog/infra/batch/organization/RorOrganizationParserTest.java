package dev.linqibin.patra.catalog.infra.batch.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import dev.linqibin.patra.catalog.domain.model.enums.ExternalIdType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationNameType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationRelationType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationStatus;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// RorOrganizationParser 单元测试。
///
/// **测试策略**：
///
/// - 验证 ROR v2 JSON 解析
/// - 验证 OrganizationAggregate 转换
/// - 验证所有子字段（names、locations、external_ids、relationships）
/// - 验证错误处理
///
/// **ROR v2 JSON 结构**：
///
/// ROR Data Dump 是一个 JSON 数组，每个元素代表一个机构记录。
/// 解析器需要支持流式读取，逐条解析机构记录。
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/v2/docs/fields">ROR Fields Documentation</a>
@DisplayName("RorOrganizationParser 单元测试")
@Timeout(2)
class RorOrganizationParserTest {

  private RorOrganizationParser parser;

  @BeforeEach
  void setUp() {
    parser = new RorOrganizationParser();
  }

  @Nested
  @DisplayName("parse() 方法测试")
  class ParseTest {

    @Test
    @DisplayName("解析单条 ROR 记录 - 应该正确转换为 OrganizationAggregate")
    void parseSingleRecord_shouldConvertToOrganizationAggregate() throws Exception {
      // Given: 最小化的 ROR v2 JSON 记录
      String json =
          """
          [
            {
              "id": "https://ror.org/03yrm5c26",
              "names": [
                {"value": "Massachusetts Institute of Technology", "types": ["ror_display"], "lang": null}
              ],
              "status": "active",
              "types": ["education", "funder"],
              "locations": [
                {
                  "geonames_id": 4931972,
                  "geonames_details": {
                    "country_code": "US",
                    "country_name": "United States",
                    "lat": 42.3736,
                    "lng": -71.1097,
                    "name": "Cambridge"
                  }
                }
              ]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      OrganizationAggregate org = results.getFirst();

      // 核心字段
      assertThat(org.getRorId().getId()).isEqualTo("03yrm5c26");
      assertThat(org.getDisplayName()).isEqualTo("Massachusetts Institute of Technology");
      assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);

      // 类型
      assertThat(org.getTypes())
          .containsExactlyInAnyOrder(OrganizationType.EDUCATION, OrganizationType.FUNDER);

      // 地理位置
      assertThat(org.getLocations()).hasSize(1);
      var location = org.getLocations().getFirst();
      assertThat(location.geonamesId()).isEqualTo(4931972);
      assertThat(location.countryCode()).isEqualTo("US");
      assertThat(location.cityName()).isEqualTo("Cambridge");
    }

    @Test
    @DisplayName("解析完整 ROR 记录 - 应该正确填充所有字段")
    void parseFullRecord_shouldPopulateAllFields() throws Exception {
      // Given: 完整的 ROR v2 JSON 记录
      String json =
          """
          [
            {
              "id": "https://ror.org/03yrm5c26",
              "admin": {
                "created": {"date": "2018-11-14", "schema_version": "1.0"},
                "last_modified": {"date": "2024-03-15", "schema_version": "2.0"}
              },
              "domains": ["mit.edu"],
              "established": 1861,
              "external_ids": [
                {"type": "isni", "preferred": "0000 0001 2341 2786", "all": ["0000 0001 2341 2786"]},
                {"type": "wikidata", "preferred": "Q49108", "all": ["Q49108"]},
                {"type": "fundref", "preferred": "100000936", "all": ["100000936"]}
              ],
              "links": [
                {"type": "website", "value": "https://www.mit.edu/"},
                {"type": "wikipedia", "value": "https://en.wikipedia.org/wiki/Massachusetts_Institute_of_Technology"}
              ],
              "locations": [
                {
                  "geonames_id": 4931972,
                  "geonames_details": {
                    "continent_code": "NA",
                    "continent_name": "North America",
                    "country_code": "US",
                    "country_name": "United States",
                    "country_subdivision_code": "MA",
                    "country_subdivision_name": "Massachusetts",
                    "lat": 42.3736,
                    "lng": -71.1097,
                    "name": "Cambridge"
                  }
                }
              ],
              "names": [
                {"value": "Massachusetts Institute of Technology", "types": ["ror_display"], "lang": null},
                {"value": "MIT", "types": ["acronym"], "lang": null},
                {"value": "Instituto Tecnológico de Massachusetts", "types": ["label"], "lang": "es"}
              ],
              "relationships": [
                {"id": "https://ror.org/042nb2s44", "label": "Lincoln Laboratory", "type": "child"}
              ],
              "status": "active",
              "types": ["education", "funder"]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      OrganizationAggregate org = results.getFirst();

      // 基础字段
      assertThat(org.getRorId().getId()).isEqualTo("03yrm5c26");
      assertThat(org.getDisplayName()).isEqualTo("Massachusetts Institute of Technology");
      assertThat(org.getEstablished()).isEqualTo(1861);

      // 域名
      assertThat(org.getDomains()).containsExactly("mit.edu");

      // 管理元数据
      assertThat(org.getAdminInfo()).isNotNull();
      assertThat(org.getAdminInfo().createdDate()).isEqualTo(LocalDate.of(2018, 11, 14));
      assertThat(org.getAdminInfo().lastModifiedDate()).isEqualTo(LocalDate.of(2024, 3, 15));

      // 名称（多个）
      assertThat(org.getNames()).hasSize(3);
      var displayName =
          org.getNames().stream()
              .filter(n -> n.types().contains(OrganizationNameType.ROR_DISPLAY))
              .findFirst();
      assertThat(displayName).isPresent();
      assertThat(displayName.get().value()).isEqualTo("Massachusetts Institute of Technology");

      var acronym =
          org.getNames().stream()
              .filter(n -> n.types().contains(OrganizationNameType.ACRONYM))
              .findFirst();
      assertThat(acronym).isPresent();
      assertThat(acronym.get().value()).isEqualTo("MIT");

      // 外部标识符
      assertThat(org.getExternalIds()).hasSize(3);
      assertThat(org.getExternalId(ExternalIdType.ISNI)).isPresent();
      assertThat(org.getExternalId(ExternalIdType.ISNI).get().preferred())
          .isEqualTo("0000 0001 2341 2786");
      assertThat(org.getExternalId(ExternalIdType.WIKIDATA)).isPresent();
      assertThat(org.getExternalId(ExternalIdType.FUNDREF)).isPresent();

      // 链接
      assertThat(org.getLinks()).hasSize(2);
      assertThat(org.getWebsiteUrl()).hasValue("https://www.mit.edu/");
      assertThat(org.getWikipediaUrl())
          .hasValue("https://en.wikipedia.org/wiki/Massachusetts_Institute_of_Technology");

      // 地理位置（完整字段）
      assertThat(org.getLocations()).hasSize(1);
      var location = org.getLocations().getFirst();
      assertThat(location.geonamesId()).isEqualTo(4931972);
      assertThat(location.continentCode()).isEqualTo("NA");
      assertThat(location.continentName()).isEqualTo("North America");
      assertThat(location.countryCode()).isEqualTo("US");
      assertThat(location.countryName()).isEqualTo("United States");
      assertThat(location.subdivisionCode()).isEqualTo("MA");
      assertThat(location.subdivisionName()).isEqualTo("Massachusetts");
      assertThat(location.cityName()).isEqualTo("Cambridge");
      assertThat(location.latitude()).isEqualByComparingTo(new BigDecimal("42.3736"));
      assertThat(location.longitude()).isEqualByComparingTo(new BigDecimal("-71.1097"));

      // 关系
      assertThat(org.getRelations()).hasSize(1);
      var relation = org.getRelations().getFirst();
      assertThat(relation.type()).isEqualTo(OrganizationRelationType.CHILD);
      assertThat(relation.relatedRorId().getId()).isEqualTo("042nb2s44");
      assertThat(relation.relatedLabel()).isEqualTo("Lincoln Laboratory");
    }

    @Test
    @DisplayName("解析多条 ROR 记录 - 应该返回多个 OrganizationAggregate")
    void parseMultipleRecords_shouldReturnMultipleResults() throws Exception {
      // Given
      String json =
          """
          [
            {
              "id": "https://ror.org/03yrm5c26",
              "names": [{"value": "MIT", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": ["education"],
              "locations": [{"geonames_id": 1, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            },
            {
              "id": "https://ror.org/02jbv0t02",
              "names": [{"value": "Harvard University", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": ["education"],
              "locations": [{"geonames_id": 2, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            },
            {
              "id": "https://ror.org/05a28rw58",
              "names": [{"value": "Stanford University", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": ["education"],
              "locations": [{"geonames_id": 3, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(3);
      assertThat(results.get(0).getDisplayName()).isEqualTo("MIT");
      assertThat(results.get(1).getDisplayName()).isEqualTo("Harvard University");
      assertThat(results.get(2).getDisplayName()).isEqualTo("Stanford University");
    }

    @Test
    @DisplayName("解析不同状态的机构 - 应该正确映射状态枚举")
    void parseDifferentStatuses_shouldMapStatusEnum() throws Exception {
      // Given
      String json =
          """
          [
            {
              "id": "https://ror.org/0active01",
              "names": [{"value": "Active Org", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": ["education"],
              "locations": [{"geonames_id": 1, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            },
            {
              "id": "https://ror.org/0inactiv2",
              "names": [{"value": "Inactive Org", "types": ["ror_display"], "lang": null}],
              "status": "inactive",
              "types": ["education"],
              "locations": [{"geonames_id": 2, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            },
            {
              "id": "https://ror.org/0withdrw3",
              "names": [{"value": "Withdrawn Org", "types": ["ror_display"], "lang": null}],
              "status": "withdrawn",
              "types": ["education"],
              "locations": [{"geonames_id": 3, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(3);
      assertThat(results.get(0).getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
      assertThat(results.get(1).getStatus()).isEqualTo(OrganizationStatus.INACTIVE);
      assertThat(results.get(2).getStatus()).isEqualTo(OrganizationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("解析所有机构类型 - 应该正确映射类型枚举")
    void parseAllOrganizationTypes_shouldMapTypeEnum() throws Exception {
      // Given
      String json =
          """
          [
            {
              "id": "https://ror.org/0multitp1",
              "names": [{"value": "Multi-type Org", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": ["archive", "company", "education", "facility", "funder", "government", "healthcare", "nonprofit", "other"],
              "locations": [{"geonames_id": 1, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      assertThat(results.getFirst().getTypes())
          .containsExactlyInAnyOrder(
              OrganizationType.ARCHIVE,
              OrganizationType.COMPANY,
              OrganizationType.EDUCATION,
              OrganizationType.FACILITY,
              OrganizationType.FUNDER,
              OrganizationType.GOVERNMENT,
              OrganizationType.HEALTHCARE,
              OrganizationType.NONPROFIT,
              OrganizationType.OTHER);
    }

    @Test
    @DisplayName("解析所有关系类型 - 应该正确映射关系枚举")
    void parseAllRelationshipTypes_shouldMapRelationEnum() throws Exception {
      // Given
      String json =
          """
          [
            {
              "id": "https://ror.org/0testorg1",
              "names": [{"value": "Test Org", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": ["education"],
              "locations": [{"geonames_id": 1, "geonames_details": {"country_code": "US", "country_name": "United States"}}],
              "relationships": [
                {"id": "https://ror.org/0parent01", "label": "Parent Org", "type": "parent"},
                {"id": "https://ror.org/0child001", "label": "Child Org", "type": "child"},
                {"id": "https://ror.org/0related1", "label": "Related Org", "type": "related"},
                {"id": "https://ror.org/0succsor1", "label": "Successor Org", "type": "successor"},
                {"id": "https://ror.org/0predcsr1", "label": "Predecessor Org", "type": "predecessor"}
              ]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      var relations = results.getFirst().getRelations();
      assertThat(relations).hasSize(5);

      assertThat(
              relations.stream().filter(r -> r.type() == OrganizationRelationType.PARENT).count())
          .isEqualTo(1);
      assertThat(relations.stream().filter(r -> r.type() == OrganizationRelationType.CHILD).count())
          .isEqualTo(1);
      assertThat(
              relations.stream().filter(r -> r.type() == OrganizationRelationType.RELATED).count())
          .isEqualTo(1);
      assertThat(
              relations.stream()
                  .filter(r -> r.type() == OrganizationRelationType.SUCCESSOR)
                  .count())
          .isEqualTo(1);
      assertThat(
              relations.stream()
                  .filter(r -> r.type() == OrganizationRelationType.PREDECESSOR)
                  .count())
          .isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCaseTest {

    @Test
    @DisplayName("解析空数组 - 应该返回空列表")
    void parseEmptyArray_shouldReturnEmptyList() throws Exception {
      // Given
      String json = "[]";
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("解析无可选字段的记录 - 应该只填充必填字段")
    void parseMinimalRecord_shouldOnlyPopulateRequiredFields() throws Exception {
      // Given: 只包含必填字段的记录
      String json =
          """
          [
            {
              "id": "https://ror.org/0minimal1",
              "names": [{"value": "Minimal Org", "types": ["ror_display"], "lang": null}],
              "status": "active",
              "types": [],
              "locations": [{"geonames_id": 1, "geonames_details": {"country_code": "US", "country_name": "United States"}}]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      OrganizationAggregate org = results.getFirst();

      // 必填字段已填充
      assertThat(org.getRorId().getId()).isEqualTo("0minimal1");
      assertThat(org.getDisplayName()).isEqualTo("Minimal Org");
      assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);

      // 可选字段为空或默认值
      assertThat(org.getEstablished()).isNull();
      assertThat(org.getDomains()).isEmpty();
      assertThat(org.getExternalIds()).isEmpty();
      assertThat(org.getRelations()).isEmpty();
      assertThat(org.getAdminInfo()).isNull();
    }

    @Test
    @DisplayName("解析多语言名称 - 应该正确保留语言信息")
    void parseMultilingualNames_shouldPreserveLanguageInfo() throws Exception {
      // Given
      String json =
          """
          [
            {
              "id": "https://ror.org/0parisi01",
              "names": [
                {"value": "Université de Paris", "types": ["ror_display"], "lang": null},
                {"value": "University of Paris", "types": ["label"], "lang": "en"},
                {"value": "パリ大学", "types": ["label"], "lang": "ja"},
                {"value": "巴黎大学", "types": ["label"], "lang": "zh"}
              ],
              "status": "active",
              "types": ["education"],
              "locations": [{"geonames_id": 1, "geonames_details": {"country_code": "FR", "country_name": "France"}}]
            }
          ]
          """;
      InputStream input = toInputStream(json);

      // When
      List<OrganizationAggregate> results = parser.parse(input).toList();

      // Then
      assertThat(results).hasSize(1);
      var names = results.getFirst().getNames();
      assertThat(names).hasSize(4);

      // 验证语言代码
      var englishName = names.stream().filter(n -> "en".equals(n.lang())).findFirst();
      assertThat(englishName).isPresent();
      assertThat(englishName.get().value()).isEqualTo("University of Paris");

      var japaneseName = names.stream().filter(n -> "ja".equals(n.lang())).findFirst();
      assertThat(japaneseName).isPresent();
      assertThat(japaneseName.get().value()).isEqualTo("パリ大学");

      var chineseName = names.stream().filter(n -> "zh".equals(n.lang())).findFirst();
      assertThat(chineseName).isPresent();
      assertThat(chineseName.get().value()).isEqualTo("巴黎大学");
    }

    @Test
    @DisplayName("解析异常 JSON - 应该抛出异常而不是静默截断")
    void parseInvalidJson_shouldThrowException() {
      // Given: 非法 JSON
      String json =
          """
          [
            {
              "id": "https://ror.org/0invalid1",
              "names": [{"value": "Invalid Org", "types": ["ror_display"], "lang": null}]
          """;
      InputStream input = toInputStream(json);

      // When & Then
      assertThatThrownBy(() -> parser.parse(input).toList())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("解析 ROR 记录失败");
    }
  }

  /// 将字符串转换为 InputStream。
  private InputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
